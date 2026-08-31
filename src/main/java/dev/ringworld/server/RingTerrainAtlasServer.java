package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.net.RingAtlasPregenerationStatusPayload;
import dev.ringworld.net.RingTerrainAtlasMetadataPayload;
import dev.ringworld.net.RingTerrainAtlasRevisionPayload;
import dev.ringworld.net.RingTerrainAtlasTilePayload;
import dev.ringworld.net.RingTerrainPreviewPayload;
import dev.ringworld.net.RingSkyProfilePayload;
import dev.ringworld.world.AtlasPregenerationAccess;
import dev.ringworld.world.AtlasPregenerationAction;
import dev.ringworld.world.AtlasPregenerationHandle;
import dev.ringworld.world.AtlasPregenerationOptions;
import dev.ringworld.world.AtlasPregenerationProgress;
import dev.ringworld.world.AtlasPregenerationState;
import dev.ringworld.world.AtlasPregenerationStatus;
import dev.ringworld.world.RingAtlasPregenerationCursor;
import dev.ringworld.world.RingTerrainAtlas;
import dev.ringworld.world.RingTerrainPreview;
import dev.ringworld.world.RingTerrainPreviewStage;
import dev.ringworld.world.RingSkyProfile;
import dev.ringworld.world.RingSkySettings;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import java.nio.file.Path;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Loader-neutral command and streaming coordinator for the authoritative atlas service. */
public final class RingTerrainAtlasServer {
    private static final int STREAM_TILES_PER_TICK = 8;
    private static final int PROGRESS_INTERVAL_TICKS = 20;
    private static final Map<UUID, ClientStream> STREAMS = new HashMap<>();
    private static final Map<UUID, ProgressObserver> PROGRESS_OBSERVERS = new HashMap<>();
    private static final Map<ServerLevel, PreviewJob> PREVIEW_JOBS = new WeakHashMap<>();
    private static final ExecutorService PREVIEW_EXECUTOR = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "RingWorld staged terrain preview");
        thread.setDaemon(true);
        return thread;
    });
    private static PayloadTransport transport = new PayloadTransport() {
        @Override public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) { return false; }
        @Override public void send(ServerPlayer player, CustomPacketPayload payload) {
            throw new IllegalStateException("RingWorld server payload transport is not configured");
        }
    };

    private RingTerrainAtlasServer() { }

    public static void configureTransport(PayloadTransport adapter) {
        if (adapter == null) throw new IllegalArgumentException("payload transport is required");
        transport = adapter;
    }

    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
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
                                .then(Commands.literal("start").executes(context -> control(
                                        context.getSource().getServer().getLevel(Level.OVERWORLD),
                                        AtlasPregenerationAction.START, context.getSource())))
                                .then(Commands.literal("pause").executes(context -> control(
                                        context.getSource().getServer().getLevel(Level.OVERWORLD),
                                        AtlasPregenerationAction.PAUSE, context.getSource())))
                                .then(Commands.literal("resume").executes(context -> control(
                                        context.getSource().getServer().getLevel(Level.OVERWORLD),
                                        AtlasPregenerationAction.RESUME, context.getSource()))))
                        .then(Commands.literal("sky")
                                .then(Commands.argument("backdrop", StringArgumentType.word())
                                        .executes(context -> setSkyBackdrop(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "backdrop")))))
                        .then(Commands.literal("sun")
                                .then(Commands.argument("style", StringArgumentType.word())
                                        .executes(context -> setSunStyle(
                                                context.getSource(),
                                                StringArgumentType.getString(context, "style"))))));
    }

    private static int setSkyBackdrop(CommandSourceStack source, String name) {
        ServerLevel world = source.getServer().getLevel(Level.OVERWORLD);
        if (world == null) {
            source.sendFailure(Component.literal("RingWorld Overworld is unavailable"));
            return 0;
        }
        RingSkyProfile.Backdrop backdrop;
        try {
            backdrop = switch (name.toLowerCase(java.util.Locale.ROOT)) {
                case "atmosphere" -> RingSkyProfile.Backdrop.ATMOSPHERE;
                case "night" -> RingSkyProfile.Backdrop.NIGHT;
                case "void" -> RingSkyProfile.Backdrop.VOID;
                default -> RingSkyProfile.Backdrop.valueOf(name.toUpperCase(java.util.Locale.ROOT));
            };
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal(
                    "Unknown sky. Use atmosphere, night, or void."));
            return 0;
        }
        RingSkyProfile profile = RingSkySettings.get(world).profile().withBackdrop(backdrop);
        publishSkyProfile(source, world, profile);
        source.sendSuccess(() -> Component.literal(
                "RingWorld sky changed to " + backdrop.label() + "."), true);
        return 1;
    }

    private static int setSunStyle(CommandSourceStack source, String name) {
        ServerLevel world = source.getServer().getLevel(Level.OVERWORLD);
        if (world == null) {
            source.sendFailure(Component.literal("RingWorld Overworld is unavailable"));
            return 0;
        }
        RingSkyProfile.LightSource sunStyle;
        try {
            sunStyle = RingSkyProfile.LightSource.valueOf(
                    name.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            source.sendFailure(Component.literal("Unknown sun style. Use small, large, or none."));
            return 0;
        }
        RingSkyProfile profile = RingSkySettings.get(world).profile().withLightSource(sunStyle);
        publishSkyProfile(source, world, profile);
        source.sendSuccess(() -> Component.literal(
                "RingWorld sun changed to " + sunStyle.label() + "."), true);
        return 1;
    }

    private static void publishSkyProfile(
            CommandSourceStack source, ServerLevel world, RingSkyProfile profile) {
        RingSkySettings.setProfile(world, profile);
        RingSkyProfilePayload payload = RingSkyProfilePayload.from(profile);
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (transport.canSend(player, RingSkyProfilePayload.ID)) transport.send(player, payload);
        }
    }

    public static void load(ServerLevel world) { RingAtlasPregenerationService.load(world); }
    public static void load(ServerLevel world, boolean allowBackgroundAutostart) {
        RingAtlasPregenerationService.load(world, allowBackgroundAutostart);
    }
    public static void unload(ServerLevel world) {
        RingAtlasPregenerationService.unload(world);
        STREAMS.entrySet().removeIf(entry -> entry.getValue().world == world);
        PROGRESS_OBSERVERS.entrySet().removeIf(entry -> entry.getValue().world == world);
        PreviewJob previewJob = PREVIEW_JOBS.remove(world);
        if (previewJob != null) previewJob.cancel();
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
        if (overworld == null) return;
        if (!transport.canSend(player, RingTerrainAtlasMetadataPayload.ID)
                || !transport.canSend(player, RingTerrainAtlasTilePayload.ID)
                || !transport.canSend(player, RingTerrainAtlasRevisionPayload.ID)
                || !transport.canSend(player, RingTerrainPreviewPayload.ID)) {
            player.connection.disconnect(Component.literal(
                    "RingWorld client terrain-atlas protocol is missing or out of date."));
            return;
        }
        RingTerrainAtlas atlas;
        try { atlas = RingAtlasPregenerationService.atlas(overworld); }
        catch (IllegalStateException ignored) { return; }
        transport.send(player, new RingTerrainAtlasMetadataPayload(atlas.worldHash(), atlas.sampleStep(),
                atlas.columns(), atlas.rows(), RingTerrainAtlas.TILE_SIZE, atlas.presentCount(), atlas.isComplete(),
                atlas.revision()));
        if (!atlas.isComplete()) sendPreview(player, overworld, atlas);
        // Geometry acknowledgement is the first point at which a client can
        // safely bind this status to a RingWorld layout.
        PROGRESS_OBSERVERS.put(player.getUUID(), new ProgressObserver(overworld));
        sendPregenerationStatus(player, overworld, Optional.empty());
    }

    private static void sendPreview(ServerPlayer player, ServerLevel world, RingTerrainAtlas atlas) {
        if (Boolean.getBoolean("ringworld.disableSeedPreview")) return;
        PreviewJob job = PREVIEW_JOBS.get(world);
        if (job != null && job.worldHash != atlas.worldHash()) {
            job.cancel();
            PREVIEW_JOBS.remove(world);
            job = null;
        }
        if (job == null) {
            job = new PreviewJob(atlas.worldHash());
            PREVIEW_JOBS.put(world, job);
            startPreviewJob(world, atlas, job);
        }
        if (job.latestData != null) {
            sendPreviewPayload(player, atlas.worldHash(), job.latestData, job.latestStage);
        }
    }

    private static void startPreviewJob(ServerLevel world, RingTerrainAtlas atlas, PreviewJob job) {
        job.future = CompletableFuture.runAsync(() -> {
            try {
                for (RingTerrainPreviewStage stage : RingTerrainPreviewStage.values()) {
                    if (job.cancelled) return;
                    RingTerrainPreview preview = RingTerrainPreviewGenerator.generate(
                            world, atlas.worldHash(), atlas.geometry(), stage);
                    if (preview == null || job.cancelled) return;
                    byte[] encoded = preview.encode();
                    if (job.cancelled) return;
                    world.getServer().execute(() -> publishPreview(world, atlas, job, stage, encoded));
                }
            } catch (CancellationException ignored) {
                // World unload and completed authoritative Atlases cancel the disposable preview.
            } catch (IOException | RuntimeException exception) {
                RingWorldMod.LOGGER.warn(
                        "Could not build staged RingWorld terrain preview; retaining the last available stage",
                        exception);
            }
        }, PREVIEW_EXECUTOR);
    }

    private static void publishPreview(ServerLevel world, RingTerrainAtlas sourceAtlas,
                                       PreviewJob job, RingTerrainPreviewStage stage,
                                       byte[] encoded) {
        if (job.cancelled || PREVIEW_JOBS.get(world) != job) return;
        RingTerrainAtlas current;
        try { current = RingAtlasPregenerationService.atlas(world); }
        catch (IllegalStateException ignored) { job.cancel(); return; }
        if (current.worldHash() != sourceAtlas.worldHash() || current.isComplete()) {
            job.cancel();
            return;
        }
        job.latestData = encoded;
        job.latestStage = stage;
        for (ServerPlayer player : world.players()) {
            if (transport.canSend(player, RingTerrainPreviewPayload.ID)) {
                sendPreviewPayload(player, current.worldHash(), encoded, stage);
            }
        }
    }

    private static void sendPreviewPayload(ServerPlayer player, long worldHash, byte[] encoded,
                                           RingTerrainPreviewStage stage) {
        if (stage == null) return;
        transport.send(player, new RingTerrainPreviewPayload(worldHash, stage.wireValue(), encoded));
        RingWorldMod.LOGGER.info("Sent RingWorld {} seed preview to {} ({} KiB compressed)",
                stage.logLabel(), player.getName().getString(),
                Math.max(1, encoded.length / 1_024));
    }

    public static void requestTiles(ServerPlayer player, long worldHash, long clientRevision,
                                    boolean cacheComplete) {
        ServerLevel overworld = player.level().getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        RingTerrainAtlas atlas;
        try { atlas = RingAtlasPregenerationService.atlas(overworld); }
        catch (IllegalStateException ignored) { return; }
        if (atlas.worldHash() != worldHash || clientRevision < 0L) return;
        Queue<RingAtlasPregenerationService.TileCoordinate> tiles = new ArrayDeque<>();
        boolean exactCompleteCache = cacheComplete && clientRevision == atlas.revision();
        if (!exactCompleteCache) {
            for (int z = 0; z < atlas.tileRows(); z++) for (int x = 0; x < atlas.tileColumns(); x++) {
                tiles.add(new RingAtlasPregenerationService.TileCoordinate(x, z));
            }
        }
        STREAMS.put(player.getUUID(), new ClientStream(overworld, tiles,
                exactCompleteCache ? clientRevision : -1L));
        if (!tiles.isEmpty()) {
            RingWorldMod.LOGGER.info("Streaming {} RingWorld terrain atlas tiles (~{} KiB) to {}", tiles.size(),
                    Math.max(1L, atlas.estimatedWireBytes() / 1_024L), player.getName().getString());
        }
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
        if (!transport.canSend(player, RingAtlasPregenerationStatusPayload.ID)) return;
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
        transport.send(player, new RingAtlasPregenerationStatusPayload(status));
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

    private static int control(ServerLevel world, AtlasPregenerationAction action,
                               net.minecraft.commands.CommandSourceStack source) {
        if (world == null) {
            source.sendFailure(Component.literal("RingWorld Overworld is unavailable"));
            return 0;
        }
        AtlasPregenerationHandle handle = RingAtlasPregenerationService.active(world).orElse(null);
        if (handle == null) {
            source.sendFailure(Component.literal("RingWorld terrain atlas pregeneration is unavailable"));
            return 0;
        }
        AtlasPregenerationState state = handle.progress().state();
        String result;
        switch (RingAtlasCommandPolicy.decide(action, state)) {
            case START -> {
                try {
                    RingAtlasPregenerationService.pregenerate(world,
                            AtlasPregenerationOptions.interactiveDefaults(), progress -> { });
                } catch (IllegalStateException exception) {
                    source.sendFailure(Component.literal(
                            "RingWorld atlas generation could not start: "
                                    + Optional.ofNullable(exception.getMessage())
                                    .orElse("the previous job has not released its resources")));
                    return 0;
                }
                result = state == AtlasPregenerationState.IDLE
                        ? "started from saved progress" : "started";
            }
            case PAUSE -> {
                handle.pause();
                result = "paused";
            }
            case RESUME -> {
                handle.resume();
                result = "resumed";
            }
            case ALREADY_COMPLETE -> result = "is already complete";
            case ALREADY_ACTIVE -> result = "is already active";
            case NOT_RUNNING -> {
                source.sendFailure(Component.literal("RingWorld atlas generation is not running: "
                        + RingAtlasPregenerationService.status(world)));
                return 0;
            }
            case NOT_PAUSED -> {
                source.sendFailure(Component.literal("RingWorld atlas generation is not paused: "
                        + RingAtlasPregenerationService.status(world)));
                return 0;
            }
            case UNSUPPORTED -> {
                source.sendFailure(Component.literal(
                        "Atlas cancellation is available from the RingWorld Map screen."));
                return 0;
            }
            default -> throw new IllegalStateException("unsupported atlas command outcome");
        }
        source.sendSuccess(() -> Component.literal("RingWorld atlas pregeneration " + result
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
            if (stream == null || stream.world != world || !transport.canSend(player, RingTerrainAtlasTilePayload.ID)) continue;
            for (int count = 0; count < STREAM_TILES_PER_TICK && !stream.tiles.isEmpty(); count++) {
                RingAtlasPregenerationService.TileCoordinate tile = stream.tiles.remove();
                stream.known.remove(tile);
                transport.send(player, new RingTerrainAtlasTilePayload(atlas.worldHash(), tile.x(), tile.z(), atlas.encodeTile(tile.x(), tile.z())));
            }
            if (stream.tiles.isEmpty() && atlas.isComplete()
                    && !RingAtlasPregenerationService.hasPendingDirtyTiles(world)) {
                if (stream.committedRevision != atlas.revision()
                        && transport.canSend(player, RingTerrainAtlasRevisionPayload.ID)) {
                    transport.send(player, new RingTerrainAtlasRevisionPayload(
                            atlas.worldHash(), atlas.revision()));
                    stream.committedRevision = atlas.revision();
                }
            }
        }
    }

    private static final class ClientStream {
        private final ServerLevel world;
        private final Queue<RingAtlasPregenerationService.TileCoordinate> tiles;
        private final Set<RingAtlasPregenerationService.TileCoordinate> known = new HashSet<>();
        private long committedRevision;
        private ClientStream(ServerLevel world, Queue<RingAtlasPregenerationService.TileCoordinate> tiles,
                             long committedRevision) {
            this.world = world;
            this.tiles = tiles;
            this.committedRevision = committedRevision;
            this.known.addAll(tiles);
        }
    }

    private static final class ProgressObserver {
        private final ServerLevel world;
        private long lastSentTick = Long.MIN_VALUE / 2;
        private AtlasPregenerationState state;
        private ProgressObserver(ServerLevel world) { this.world = world; }
    }

    private static final class PreviewJob {
        private final long worldHash;
        private volatile boolean cancelled;
        private CompletableFuture<Void> future;
        private byte[] latestData;
        private RingTerrainPreviewStage latestStage;

        private PreviewJob(long worldHash) { this.worldHash = worldHash; }

        private void cancel() {
            cancelled = true;
            if (future != null) future.cancel(true);
        }
    }

    /** Narrow loader-owned payload capability and delivery adapter. */
    public interface PayloadTransport {
        boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type);
        void send(ServerPlayer player, CustomPacketPayload payload);
    }
}
