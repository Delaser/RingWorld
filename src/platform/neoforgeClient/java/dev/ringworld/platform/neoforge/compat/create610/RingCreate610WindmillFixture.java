package dev.ringworld.platform.neoforge.compat.create610;

import com.simibubi.create.content.contraptions.ControlledContraptionEntity;
import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.engine_room.flywheel.api.backend.Backend;
import dev.engine_room.flywheel.api.backend.BackendManager;
import dev.ringworld.RingWorldMod;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.compat.Screenshot;
import dev.ringworld.client.mixin.CreateWorldScreenInvoker;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/** Disposable exact-tuple control for a real wind-driven glued Windmill Bearing. */
public final class RingCreate610WindmillFixture {
    public static final String ENABLE_PROPERTY = "ringworld.createCompatWindmill";
    private static final String WORLD_NAME = "RingWorld Create Windmill Control";
    private static final BlockPos BEARING_POS = new BlockPos(640, 120, 100);
    private static final RingCreate610WindmillFixture INSTANCE = new RingCreate610WindmillFixture();

    private boolean worldScreenOpened;
    private boolean worldStarted;
    private boolean setupRequested;
    private volatile boolean setupReady;
    private volatile boolean assemblyReady;
    private volatile boolean stopReady;
    private volatile boolean restorationReady;
    private volatile String failure;
    private volatile UUID entityUuid;
    private volatile int entityId = -1;
    private volatile float generatedSpeed;
    private int stage;
    private int ticks;
    private int stageTicks;
    private Entity clientIdentity;
    private int visualIdentity = -1;
    private float firstAngle = Float.NaN;

    private RingCreate610WindmillFixture() { }

    public static RingCreate610WindmillFixture instance() { return INSTANCE; }

    public boolean startWorldIfEnabled(Minecraft client) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY) || client.level != null || worldStarted) return false;
        if (!worldScreenOpened) {
            if (!(client.screen instanceof TitleScreen)) return true;
            CreateWorldScreen.openFresh(client, client.screen);
            worldScreenOpened = true;
            return true;
        }
        if (client.screen instanceof CreateWorldScreen screen) {
            WorldCreationUiState creator = screen.getUiState();
            creator.setName(WORLD_NAME);
            creator.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            creator.setAllowCommands(true);
            creator.setSeed("-2162056627494116761");
            RingCreate610ClientDiagnostics.reset();
            ((CreateWorldScreenInvoker) screen).ringworld$createLevel();
            worldStarted = true;
        }
        return true;
    }

    public boolean tick(Minecraft client) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) return false;
        if (startWorldIfEnabled(client)) return true;
        client.options.pauseOnLostFocus = false;
        if (client.screen instanceof PauseScreen) client.setScreen(null);
        if (++ticks > 1_600) return finish(client, false, "timeout stage=" + stage);
        if (failure != null) return finish(client, false, failure);
        if (client.level == null || client.player == null || client.screen != null
                || ClientRingState.geometry() == null) return true;
        stageTicks++;
        switch (stage) {
            case 0 -> requestSetup(client);
            case 1 -> waitForAssembly(client);
            case 2 -> proveLiveWindRotation(client);
            case 3 -> requestAlignedDisassembly(client);
            case 4 -> verifyRestoration(client);
            default -> { return finish(client, false, "invalid stage=" + stage); }
        }
        return true;
    }

    private void requestSetup(Minecraft client) {
        if (setupRequested) return;
        setupRequested = true;
        var server = client.getSingleplayerServer();
        UUID playerUuid = client.player.getUUID();
        server.execute(() -> guarded("windmill setup", () -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayer(playerUuid);
            if (player == null) throw new IllegalStateException("missing windmill fixture player");
            player.teleportTo(level, BEARING_POS.getX() + 0.5, 123.0,
                    BEARING_POS.getZ() - 12.0, java.util.Set.of(), 0.0F, 0.0F);
            for (int x = BEARING_POS.getX() - 12; x <= BEARING_POS.getX() + 12; x++) {
                for (int z = BEARING_POS.getZ() - 16; z <= BEARING_POS.getZ() + 12; z++) {
                    level.setBlockAndUpdate(new BlockPos(x, 118, z), Blocks.SMOOTH_STONE.defaultBlockState());
                    for (int y = 119; y <= 130; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
            level.setBlockAndUpdate(BEARING_POS,
                    withProperty(block("create:windmill_bearing").defaultBlockState(), "facing", "up"));
            expectedStates().forEach(level::setBlockAndUpdate);
            for (Edge edge : edges()) {
                if (!level.addFreshEntity(new SuperGlueEntity(
                        level, SuperGlueEntity.span(edge.first(), edge.second())))) {
                    throw new IllegalStateException("could not add windmill glue " + edge);
                }
            }
            setupReady = true;
            RingWorldMod.LOGGER.info(
                    "[create-windmill] placed bearing={} blocks={} glueEdges={} sails=8",
                    BEARING_POS, inventory(expectedStates()), edges());
        }));
        advance(1);
    }

    private void waitForAssembly(Minecraft client) {
        if (assemblyReady) {
            advance(2);
            return;
        }
        if (!setupReady || stageTicks < 10 || stageTicks % 5 != 0) return;
        var server = client.getSingleplayerServer();
        server.execute(() -> guarded("windmill assembly", () -> {
            ServerLevel level = server.overworld();
            if (!allGlueEdges(level)) return;
            BlockEntity bearing = requireBearing(level);
            if (!(boolean) invoke(bearing, "isRunning")) invoke(bearing, "assemble");
            ControlledContraptionEntity entity =
                    (ControlledContraptionEntity) invoke(bearing, "getMovedContraption");
            if (entity == null) return;
            if (entity.getContraption().getBlocks().size() != expectedStates().size()) {
                throw new IllegalStateException("windmill capture count="
                        + entity.getContraption().getBlocks().size());
            }
            for (BlockPos source : expectedStates().keySet()) {
                if (!level.getBlockState(source).isAir()) {
                    throw new IllegalStateException("windmill source remained " + source);
                }
            }
            generatedSpeed = ((Number) invoke(bearing, "getGeneratedSpeed")).floatValue();
            if (Math.abs(generatedSpeed) < 1.0F) {
                throw new IllegalStateException("windmill did not generate speed: " + generatedSpeed);
            }
            entityUuid = entity.getUUID();
            entityId = entity.getId();
            assemblyReady = true;
            RingWorldMod.LOGGER.info(
                    "[create-windmill] assembled entity={}/{} type={} blocks={} sails=8 generatedSpeed={}",
                    entity.getId(), entity.getUUID(), entity.getContraption().getClass().getName(),
                    entity.getContraption().getBlocks().size(), generatedSpeed);
        }));
    }

    private void proveLiveWindRotation(Minecraft client) {
        if (!assemblyReady) return;
        Entity entity = client.level.getEntity(entityId);
        if (!(entity instanceof ControlledContraptionEntity controlled)
                || !entity.getUUID().equals(entityUuid)) return;
        int visual = RingCreate610ClientDiagnostics.visualIdentity(entityId);
        if (visual < 0 || RingCreate610ClientDiagnostics.visualCreateCount(entityId) != 1) return;
        if (clientIdentity == null) {
            clientIdentity = entity;
            visualIdentity = visual;
            firstAngle = controlled.getAngle(1.0F);
            return;
        }
        if (entity != clientIdentity || visual != visualIdentity
                || RingCreate610ClientDiagnostics.visualDeleteCount(entityId) != 0) {
            finish(client, false, "windmill identity/visual discontinuity");
            return;
        }
        float angle = controlled.getAngle(1.0F);
        List<RingCreate610ClientDiagnostics.EntityTransformSample> transforms =
                RingCreate610ClientDiagnostics.entityTransformSamples(entityId);
        RingCreate610FixtureProjection.Aim aim = RingCreate610FixtureProjection.aim(
                ClientRingState.geometry(), client.gameRenderer.getMainCamera().getPosition(),
                targetPoints(controlled), 0.0, client.getMainRenderTarget().width,
                client.getMainRenderTarget().height, 70.0);
        client.player.setYRot(aim.yaw());
        client.player.yRotO = aim.yaw();
        client.player.setYHeadRot(aim.yaw());
        client.player.setXRot(aim.pitch());
        client.player.xRotO = aim.pitch();
        if (angularDistance(firstAngle, angle) < 35.0F || transforms.size() < 3) return;
        RingCreate610FixtureProjection.Projection projection = aim.projection();
        if (!projection.centerInViewport()
                || projection.pointsInViewport() != projection.totalPoints()
                || projection.width() < 180.0 || projection.height() < 20.0) {
            finish(client, false, "windmill projection missed target " + projection.logValue());
            return;
        }
        String name = "ringworld-create-windmill-control";
        var transform = transforms.get(transforms.size() - 1);
        RingWorldMod.LOGGER.info(
                "[create-windmill] capture-proof name={} backend={} entity={}/{} object={} visual={} "
                        + "angleBefore={} angleAfter={} generatedSpeed={} transformIndex={} matrix={} "
                        + "expectedVisible=true projectedBounds={} poseSanityRoi=320/180/960/540 "
                        + "yaw={} pitch={} renderMembership=true removed=false",
                name, backend(), entity.getId(), entity.getUUID(), System.identityHashCode(entity),
                visual, firstAngle, angle, generatedSpeed, transform.transformIndex(), transform.matrix(),
                projection.logValue(), aim.yaw(), aim.pitch());
        Screenshot.grab(client.gameDirectory, name + ".png", client.getMainRenderTarget(), 1,
                message -> RingWorldMod.LOGGER.info("[create-windmill] screenshot {}", message.getString()));
        advance(3);
    }

    private void requestAlignedDisassembly(Minecraft client) {
        if (stopReady) {
            advance(4);
            return;
        }
        if (stageTicks % 5 != 0) return;
        var server = client.getSingleplayerServer();
        server.execute(() -> guarded("windmill stop", () -> {
            BlockEntity bearing = requireBearing(server.overworld());
            if (!(boolean) invoke(bearing, "isNearInitialAngle")) return;
            invoke(bearing, "disassemble");
            stopReady = true;
            RingWorldMod.LOGGER.info(
                    "[create-windmill] aligned stop/disassemble requested angle={} generatedSpeed={}",
                    "near-initial", generatedSpeed);
        }));
    }

    private void verifyRestoration(Minecraft client) {
        if (restorationReady) {
            finish(client, true, "backend=" + backend() + " realWindmill=true glued=true "
                    + "windDriven=true liveRotation=true capturedBlocks=" + expectedStates().size()
                    + " restoration=true");
            return;
        }
        if (!stopReady || stageTicks % 5 != 0) return;
        var server = client.getSingleplayerServer();
        server.execute(() -> guarded("windmill restoration", () -> {
            ServerLevel level = server.overworld();
            if (invoke(requireBearing(level), "getMovedContraption") != null) return;
            for (Map.Entry<BlockPos, BlockState> entry : expectedStates().entrySet()) {
                if (level.getBlockState(entry.getKey()) != entry.getValue()) return;
            }
            if (!allGlueEdges(level)) return;
            RingWorldMod.LOGGER.info(
                    "[create-windmill] restoration PASS blocks={} glueEdges={} sourceStatesExact=true",
                    inventory(expectedStates()), edges());
            restorationReady = true;
        }));
    }

    private static BlockEntity requireBearing(ServerLevel level) {
        BlockEntity blockEntity = level.getBlockEntity(BEARING_POS);
        if (blockEntity != null && blockEntity.getClass().getName().equals(
                "com.simibubi.create.content.contraptions.bearing.WindmillBearingBlockEntity")) {
            return blockEntity;
        }
        throw new IllegalStateException("missing WindmillBearingBlockEntity: " + blockEntity);
    }

    private static Object invoke(Object target, String name) {
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("could not invoke " + name + " on " + target, failure);
        }
    }

    private static Map<BlockPos, BlockState> expectedStates() {
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        BlockPos root = BEARING_POS.above();
        states.put(root, withProperty(block("create:linear_chassis").defaultBlockState(), "axis", "y"));
        for (Direction direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            states.put(root.relative(direction), block("create:white_sail").defaultBlockState());
            states.put(root.relative(direction, 2), block("create:white_sail").defaultBlockState());
        }
        states.put(root.relative(Direction.NORTH, 3), Blocks.GOLD_BLOCK.defaultBlockState());
        states.put(root.relative(Direction.SOUTH, 3), Blocks.MAGENTA_CONCRETE.defaultBlockState());
        states.put(root.relative(Direction.EAST, 3), Blocks.LIME_CONCRETE.defaultBlockState());
        states.put(root.relative(Direction.WEST, 3), Blocks.AMETHYST_BLOCK.defaultBlockState());
        return states;
    }

    private static List<Edge> edges() {
        List<Edge> edges = new ArrayList<>();
        BlockPos root = BEARING_POS.above();
        for (Direction direction : List.of(Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST)) {
            edges.add(new Edge(root, root.relative(direction)));
            edges.add(new Edge(root.relative(direction), root.relative(direction, 2)));
            edges.add(new Edge(root.relative(direction, 2), root.relative(direction, 3)));
        }
        return List.copyOf(edges);
    }

    private static List<net.minecraft.world.phys.Vec3> targetPoints(
            ControlledContraptionEntity entity) {
        List<net.minecraft.world.phys.Vec3> points = new ArrayList<>();
        for (BlockPos local : entity.getContraption().getBlocks().keySet()) {
            for (int x = 0; x <= 1; x++) {
                for (int y = 0; y <= 1; y++) {
                    for (int z = 0; z <= 1; z++) {
                        points.add(entity.toGlobalVector(new net.minecraft.world.phys.Vec3(
                                local.getX() + x, local.getY() + y, local.getZ() + z), 1.0F));
                    }
                }
            }
        }
        return List.copyOf(points);
    }

    private static boolean allGlueEdges(ServerLevel level) {
        for (Edge edge : edges()) {
            if (!SuperGlueEntity.isGlued(level, edge.first(), direction(edge), null)) return false;
        }
        return true;
    }

    private static Direction direction(Edge edge) {
        int dx = edge.second().getX() - edge.first().getX();
        int dz = edge.second().getZ() - edge.first().getZ();
        return Direction.fromDelta(dx, 0, dz);
    }

    private static Block block(String id) {
        Block block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                net.minecraft.resources.ResourceLocation.parse(id));
        if (block == Blocks.AIR) throw new IllegalStateException("missing block " + id);
        return block;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static BlockState withProperty(BlockState state, String name, String value) {
        Property property = state.getProperties().stream()
                .filter(candidate -> candidate.getName().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalStateException("missing property " + name + " on " + state));
        Comparable parsed = (Comparable) property.getValue(value).orElseThrow();
        return state.setValue(property, parsed);
    }

    private static String inventory(Map<BlockPos, BlockState> states) {
        return states.entrySet().stream().map(entry -> entry.getKey() + "="
                + net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(entry.getValue().getBlock()))
                .toList().toString();
    }

    private static float angularDistance(float first, float second) {
        float delta = Math.abs(first - second) % 360.0F;
        return Math.min(delta, 360.0F - delta);
    }

    private static String backend() {
        return Backend.REGISTRY.getIdOrThrow(BackendManager.currentBackend()).toString();
    }

    private void guarded(String phase, Runnable action) {
        try { action.run(); }
        catch (Throwable throwable) {
            failure = phase + ": " + throwable;
            RingWorldMod.LOGGER.error("[create-windmill] {} failed", phase, throwable);
        }
    }

    private void advance(int next) { stage = next; stageTicks = 0; }

    private static boolean finish(Minecraft client, boolean pass, String detail) {
        RingWorldMod.LOGGER.info("[create-windmill] result={} {}", pass, detail);
        client.stop();
        return true;
    }

    private record Edge(BlockPos first, BlockPos second) { }
}
