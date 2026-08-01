package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.net.RingAtlasPregenerationStatusPayload;
import dev.ringworld.net.RingTerrainAtlasMetadataPayload;
import dev.ringworld.net.RingTerrainAtlasTilePayload;
import dev.ringworld.world.AtlasPregenerationAccess;
import dev.ringworld.world.AtlasPregenerationAction;
import dev.ringworld.world.AtlasPregenerationHandle;
import dev.ringworld.world.AtlasPregenerationOptions;
import dev.ringworld.world.AtlasPregenerationProgress;
import dev.ringworld.world.AtlasPregenerationState;
import dev.ringworld.world.AtlasPregenerationStatus;
import dev.ringworld.world.RingAtlasPregenerationCursor;
import dev.ringworld.world.RingTerrainAtlas;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

/** Fabric command, lifecycle, and network adapter for the authoritative atlas service. */
public final class RingTerrainAtlasServer {
    private static final int STREAM_TILES_PER_TICK = 8;
    private static final int PROGRESS_INTERVAL_TICKS = 20;
    private static final Map<UUID, ClientStream> STREAMS = new HashMap<>();
    private static final Map<UUID, ProgressObserver> PROGRESS_OBSERVERS = new HashMap<>();

    private RingTerrainAtlasServer() { }

    public static void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(Commands.literal("ringworld")
                        .requires(source -> source.permissions().hasPermission(
                                new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                        .then(Commands.literal("atlas")
                                .then(Commands.literal("status").executes(context -> {
                                    ServerLevel world = context.getSource().getServer().getLevel(Level.OVERWORLD);
                                    context.getSource().sendSuccess(() -> Component.literal(world == null
                                            ? "RingWorld Overworld is unavailable"
                                            : "RingWorld atlas: " + RingAtlasPregenerationService.status(world)), false);
                                    return world == null ? 0 : 1;
                                }))
                                .then(Commands.literal("pause").executes(context -> control(
                                        context.getSource().getServer().getLevel(Level.OVERWORLD), true, context.getSource())))
                                .then(Commands.literal("resume").executes(context -> control(
                                        context.getSource().getServer().getLevel(Level.OVERWORLD), false, context.getSource()))))));
    }

    public static void load(ServerLevel world) { RingAtlasPregenerationService.load(world); }
    public static void load(ServerLevel world, boolean allowBackgroundAutostart) {
        RingAtlasPregenerationService.load(world, allowBackgroundAutostart);
    }
    public static void unload(ServerLevel world) {
        RingAtlasPregenerationService.unload(world);
        STREAMS.entrySet().removeIf(entry -> entry.getValue().world == world);
        PROGRESS_OBSERVERS.entrySet().removeIf(entry -> entry.getValue().world == world);
    }
    public static void captureLoadedChunk(ServerLevel world, LevelChunk chunk) {
        RingAtlasPregenerationService.captureLoadedChunk(world, chunk);
    }

    public static void tick(ServerLevel world) {
        RingAtlasPregenerationService.tick(world);
        if (world.getGameTime() % RingAtlasPregenerationService.TILE_PUBLICATION_INTERVAL_TICKS == 0) {
            queueDirtyTiles(world, RingAtlasPregenerationService.drainDirtyTiles(world));
        }
        streamTiles(world);
        publishObservedStatus(world);
    }

    /** Sent after geometry acknowledgement, before the client asks for missing tiles. */
    public static void sendMetadata(ServerPlayer player) {
        ServerLevel overworld = player.level().getServer().getLevel(Level.OVERWORLD);
        if (overworld == null || !ServerPlayNetworking.canSend(player, RingTerrainAtlasMetadataPayload.ID)) return;
        RingTerrainAtlas atlas;
        try { atlas = RingAtlasPregenerationService.atlas(overworld); }
        catch (IllegalStateException ignored) { return; }
        ServerPlayNetworking.send(player, new RingTerrainAtlasMetadataPayload(atlas.worldHash(), atlas.sampleStep(),
                atlas.columns(), atlas.rows(), RingTerrainAtlas.TILE_SIZE, atlas.presentCount(), atlas.isComplete()));
        // Geometry acknowledgement is the first point at which a client can
        // safely bind this status to a RingWorld layout.
        PROGRESS_OBSERVERS.put(player.getUUID(), new ProgressObserver(overworld));
        sendPregenerationStatus(player, overworld, Optional.empty());
    }

    public static void requestTiles(ServerPlayer player, long worldHash, boolean cacheComplete) {
        ServerLevel overworld = player.level().getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        RingTerrainAtlas atlas;
        try { atlas = RingAtlasPregenerationService.atlas(overworld); }
        catch (IllegalStateException ignored) { return; }
        if (atlas.worldHash() != worldHash) return;
        if (cacheComplete) {
            STREAMS.remove(player.getUUID());
            return;
        }
        Queue<RingAtlasPregenerationService.TileCoordinate> tiles = new ArrayDeque<>();
        for (int z = 0; z < atlas.tileRows(); z++) for (int x = 0; x < atlas.tileColumns(); x++) {
            tiles.add(new RingAtlasPregenerationService.TileCoordinate(x, z));
        }
        STREAMS.put(player.getUUID(), new ClientStream(overworld, tiles));
        RingWorldMod.LOGGER.info("Streaming {} RingWorld terrain atlas tiles (~{} KiB) to {}", tiles.size(),
                Math.max(1L, atlas.estimatedWireBytes() / 1_024L), player.getName().getString());
    }

    /** Starts observing status; the request is ignored outside the loaded RingWorld Overworld. */
    public static void requestPregenerationStatus(ServerPlayer player, long worldHash) {
        ServerLevel world = player.level();
        if (world.dimension() != Level.OVERWORLD) return;
        RingTerrainAtlas atlas;
        try { atlas = RingAtlasPregenerationService.atlas(world); }
        catch (IllegalStateException ignored) { return; }
        if (atlas.worldHash() != worldHash) return;
        PROGRESS_OBSERVERS.put(player.getUUID(), new ProgressObserver(world));
        sendPregenerationStatus(player, world, Optional.empty());
    }

    /** Server-thread action gateway. Every request rechecks world and player authority. */
    public static void controlPregeneration(ServerPlayer player, long worldHash, AtlasPregenerationAction action) {
        ServerLevel world = player.level();
        if (world.dimension() != Level.OVERWORLD) return;
        RingTerrainAtlas atlas;
        try { atlas = RingAtlasPregenerationService.atlas(world); }
        catch (IllegalStateException ignored) { return; }
        PROGRESS_OBSERVERS.put(player.getUUID(), new ProgressObserver(world));
        if (atlas.worldHash() != worldHash) {
            sendPregenerationStatus(player, world, Optional.of("This map belongs to another RingWorld layout."));
            return;
        }
        if (!canControl(player)) {
            sendPregenerationStatus(player, world, Optional.of(
                    "You can view progress, but only the world owner or a gamemaster can control generation."));
            return;
        }
        try {
            AtlasPregenerationHandle handle = RingAtlasPregenerationService.active(world).orElse(null);
            AtlasPregenerationState state = handle == null ? null : handle.progress().state();
            switch (action) {
                case START -> {
                    if (state == AtlasPregenerationState.COMPLETE) {
                        sendPregenerationStatus(player, world, Optional.of("The complete atlas is already saved."));
                        return;
                    }
                    if (state == AtlasPregenerationState.RUNNING || state == AtlasPregenerationState.PAUSED
                            || state == AtlasPregenerationState.SAVING) {
                        sendPregenerationStatus(player, world, Optional.of("Generation is already active."));
                        return;
                    }
                    // Mode is an adapter intent, not permission to make a second
                    // writer: matching conservative policies reuse the loaded job.
                    RingAtlasPregenerationService.pregenerate(world,
                            AtlasPregenerationOptions.interactiveDefaults(), progress -> { });
                }
                case PAUSE -> {
                    if (state != AtlasPregenerationState.RUNNING) {
                        sendPregenerationStatus(player, world, Optional.of("Generation is not currently running."));
                        return;
                    }
                    handle.pause();
                }
                case RESUME -> {
                    if (state != AtlasPregenerationState.PAUSED) {
                        sendPregenerationStatus(player, world, Optional.of("Generation is not paused."));
                        return;
                    }
                    handle.resume();
                }
                case CANCEL -> {
                    if (state != AtlasPregenerationState.RUNNING && state != AtlasPregenerationState.PAUSED) {
                        sendPregenerationStatus(player, world, Optional.of("There is no running generation to cancel."));
                        return;
                    }
                    handle.cancel();
                }
            }
            sendPregenerationStatus(player, world, Optional.empty());
        } catch (RuntimeException exception) {
            RingWorldMod.LOGGER.warn("Rejected atlas generation action {} from {}", action,
                    player.getName().getString(), exception);
            sendPregenerationStatus(player, world, Optional.of(
                    Optional.ofNullable(exception.getMessage()).orElse("Generation request could not be completed.")));
        }
    }

    /** Fabric lifecycle adapter calls this on disconnect so old observer state cannot leak into a new session. */
    public static void clearPlayer(ServerPlayer player) {
        STREAMS.remove(player.getUUID());
        PROGRESS_OBSERVERS.remove(player.getUUID());
    }

    public static String status(ServerLevel world) { return RingAtlasPregenerationService.status(world); }

    private static boolean canControl(ServerPlayer player) {
        boolean integratedOwner = player.level().getServer().isSingleplayer()
                && player.level().getServer().isSingleplayerOwner(new NameAndId(player.getGameProfile()));
        boolean gamemaster = player.permissions().hasPermission(
                new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS));
        return AtlasPregenerationAccess.canControl(integratedOwner, gamemaster);
    }

    private static void publishObservedStatus(ServerLevel world) {
        long tick = world.getGameTime();
        for (ServerPlayer player : world.players()) {
            ProgressObserver observer = PROGRESS_OBSERVERS.get(player.getUUID());
            if (observer == null || observer.world != world) continue;
            AtlasPregenerationState current = currentState(world);
            if (observer.state != current || tick - observer.lastSentTick >= PROGRESS_INTERVAL_TICKS) {
                sendPregenerationStatus(player, world, Optional.empty());
            }
        }
    }

    private static AtlasPregenerationState currentState(ServerLevel world) {
        return RingAtlasPregenerationService.active(world).map(handle -> handle.progress().state())
                .orElse(AtlasPregenerationState.IDLE);
    }

    private static void sendPregenerationStatus(ServerPlayer player, ServerLevel world, Optional<String> message) {
        if (!ServerPlayNetworking.canSend(player, RingAtlasPregenerationStatusPayload.ID)) return;
        RingTerrainAtlas atlas;
        try { atlas = RingAtlasPregenerationService.atlas(world); }
        catch (IllegalStateException ignored) { return; }
        long chunks = RingAtlasPregenerationCursor.checkedTotalChunks(atlas.geometry().circumferenceChunks(),
                atlas.geometry().widthChunks());
        AtlasPregenerationProgress progress = RingAtlasPregenerationService.active(world)
                .map(AtlasPregenerationHandle::progress)
                .orElseGet(() -> AtlasPregenerationProgress.snapshot(AtlasPregenerationState.IDLE, 0, chunks,
                        atlas.presentCount(), atlas.presentCount(), atlas.cellCount(),
                        java.time.Duration.ZERO, Optional.empty()));
        AtlasPregenerationStatus status = new AtlasPregenerationStatus(atlas.worldHash(),
                atlas.geometry().circumferenceBlocks(), atlas.geometry().widthBlocks(), RingTerrainAtlas.FORMAT_VERSION,
                atlas.sampleStep(), chunks, atlas.presentChunkCount(), progress, canControl(player), message);
        ServerPlayNetworking.send(player, new RingAtlasPregenerationStatusPayload(status));
        ProgressObserver observer = PROGRESS_OBSERVERS.get(player.getUUID());
        if (observer != null && observer.world == world) {
            observer.lastSentTick = world.getGameTime();
            observer.state = progress.state();
        }
    }

    // Kept as package-visible adapter compatibility seams for storage tests;
    // the authoritative path policy and all load/save work live in the service.
    static Path cachePath(Path dimensionPath) { return RingAtlasPregenerationService.cachePath(dimensionPath); }
    static Path legacyCachePath(Path worldRoot) { return RingAtlasPregenerationService.legacyCachePath(worldRoot); }

    private static int control(ServerLevel world, boolean pause, net.minecraft.commands.CommandSourceStack source) {
        if (world == null) {
            source.sendFailure(Component.literal("RingWorld Overworld is unavailable"));
            return 0;
        }
        AtlasPregenerationHandle handle = RingAtlasPregenerationService.active(world).orElse(null);
        if (handle == null) {
            source.sendFailure(Component.literal("RingWorld terrain atlas pregeneration is unavailable"));
            return 0;
        }
        if (pause) handle.pause(); else handle.resume();
        String action = handle.progress().state() == dev.ringworld.world.AtlasPregenerationState.IDLE
                ? "remains idle (background generation is disabled)"
                : (pause ? "paused" : "resumed");
        source.sendSuccess(() -> Component.literal("RingWorld atlas pregeneration " + action
                + ": " + RingAtlasPregenerationService.status(world)), true);
        return 1;
    }

    private static void queueDirtyTiles(ServerLevel world, Set<RingAtlasPregenerationService.TileCoordinate> dirty) {
        if (dirty.isEmpty()) return;
        for (ClientStream stream : STREAMS.values()) {
            if (stream.world != world) continue;
            for (RingAtlasPregenerationService.TileCoordinate tile : dirty) if (stream.known.add(tile)) stream.tiles.add(tile);
        }
    }

    private static void streamTiles(ServerLevel world) {
        RingTerrainAtlas atlas;
        try { atlas = RingAtlasPregenerationService.atlas(world); }
        catch (IllegalStateException ignored) { return; }
        for (ServerPlayer player : world.players()) {
            ClientStream stream = STREAMS.get(player.getUUID());
            if (stream == null || stream.world != world || !ServerPlayNetworking.canSend(player, RingTerrainAtlasTilePayload.ID)) continue;
            for (int count = 0; count < STREAM_TILES_PER_TICK && !stream.tiles.isEmpty(); count++) {
                RingAtlasPregenerationService.TileCoordinate tile = stream.tiles.remove();
                stream.known.remove(tile);
                ServerPlayNetworking.send(player, new RingTerrainAtlasTilePayload(atlas.worldHash(), tile.x(), tile.z(), atlas.encodeTile(tile.x(), tile.z())));
            }
            // Completion may occur between 20-tick publication epochs. Keep
            // this stream alive until the service has exposed those final
            // dirty tiles to this adapter queue.
            if (stream.tiles.isEmpty() && atlas.isComplete()
                    && !RingAtlasPregenerationService.hasPendingDirtyTiles(world)) {
                STREAMS.remove(player.getUUID());
            }
        }
    }

    private static final class ClientStream {
        private final ServerLevel world;
        private final Queue<RingAtlasPregenerationService.TileCoordinate> tiles;
        private final Set<RingAtlasPregenerationService.TileCoordinate> known = new HashSet<>();
        private ClientStream(ServerLevel world, Queue<RingAtlasPregenerationService.TileCoordinate> tiles) {
            this.world = world;
            this.tiles = tiles;
            this.known.addAll(tiles);
        }
    }

    private static final class ProgressObserver {
        private final ServerLevel world;
        private long lastSentTick = Long.MIN_VALUE / 2;
        private AtlasPregenerationState state;
        private ProgressObserver(ServerLevel world) { this.world = world; }
    }
}
