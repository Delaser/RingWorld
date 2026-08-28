package dev.ringworld.platform.neoforge.compat.create610;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.foundation.blockEntity.IMultiBlockEntityContainer;
import dev.ringworld.RingWorldMod;
import dev.ringworld.server.RingBlockEntityLoadContext;
import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingBlockCoordinates;
import dev.ringworld.world.RingGeometry;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/** Bounded server-only seam formation and controller-persistence fixture. */
final class RingCreate610ServerFixture {
    private static final int Y = 120;

    private RingCreate610ServerFixture() { }

    static void verify(ServerLevel level) {
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        int circumference = geometry.circumferenceBlocks();
        if (circumference != 2048) {
            throw new IllegalStateException("Create fixture requires its disposable 2048-block ring");
        }

        verifyBeltDirection(level, geometry, circumference - 3, 1, 80, "positive chart");
        verifyBeltDirection(level, geometry, 1, circumference - 3, 84, "negative chart");
        verifyTank(level, geometry, circumference - 1, 92, true, "seam tank");
        verifyTank(level, geometry, 24, 100, false, "non-seam tank baseline");
        verifyVaultNegativeControl(level, 40, 108);
        RingWorldMod.LOGGER.info(
                "[create-compat-fixture] PASS belts=both-directions tanks=seam+baseline "
                        + "vault=unmodified controllerNbt=canonical");
    }

    private static void verifyBeltDirection(ServerLevel level, RingGeometry geometry,
                                            int startX, int endX, int z, String label) {
        int circumference = geometry.circumferenceBlocks();
        for (int x = -5; x <= 5; x++) {
            clear(level, canonicalX(startX + x, geometry), z);
            clear(level, canonicalX(endX + x, geometry), z);
        }

        BlockState shaft = block("shaft").defaultBlockState()
                .setValue(BlockStateProperties.AXIS, Direction.Axis.Z);
        BlockPos start = new BlockPos(startX, Y, z);
        BlockPos end = new BlockPos(endX, Y, z);
        level.getChunkAt(start);
        level.getChunkAt(end);
        level.setBlock(start, shaft, Block.UPDATE_ALL);
        level.setBlock(end, shaft, Block.UPDATE_ALL);

        if (!BeltConnectorItem.canConnect(level, start, end)) {
            throw new IllegalStateException(label + " seam belt did not validate");
        }
        BeltConnectorItem.createBelts(level, start, end);
        BeltBlock.initBelt(level, start);

        List<BlockEntity> segments = new ArrayList<>();
        int step = startX < endX && endX - startX < circumference / 2 ? 1
                : startX > endX && startX - endX < circumference / 2 ? -1
                : startX > endX ? 1 : -1;
        int chartEnd = endX;
        while ((long) (chartEnd - startX) * step < 0) chartEnd += step * circumference;
        for (int x = startX; ; x += step) {
            BlockPos canonical = new BlockPos(canonicalX(x, geometry), Y, z);
            BlockEntity belt = level.getBlockEntity(canonical);
            if (!(belt instanceof RingCreate610BeltAccess access)) {
                throw new IllegalStateException(label + " missing belt segment at " + canonical);
            }
            requireCanonical(access.getController(), circumference, label + " belt controller");
            segments.add(belt);
            if (x == chartEnd) break;
        }
        if (segments.size() < 3) {
            throw new IllegalStateException(label + " belt did not traverse the seam locally");
        }

        BlockEntity persisted = segments.stream().filter(segment -> {
            CompoundTag tag = segment.saveWithFullMetadata(level.registryAccess());
            return !tag.getBoolean("IsController");
        }).findFirst().orElseThrow();
        BlockPos persistedController = NbtUtils.readBlockPos(
                persisted.saveWithFullMetadata(level.registryAccess()), "Controller").orElseThrow();
        verifyControllerReadAndWriteRepair(level, RingWorldServer.geometryFor(level), persisted,
                persistedController, circumference, label + " belt");
    }

    private static void verifyTank(ServerLevel level, RingGeometry geometry,
                                   int originX, int z, boolean seam, String label) {
        int circumference = geometry.circumferenceBlocks();
        BlockState tank = block("fluid_tank").defaultBlockState();
        List<BlockPos> positions = new ArrayList<>();
        for (int dx = 0; dx < 2; dx++) {
            for (int dy = 0; dy < 2; dy++) {
                for (int dz = 0; dz < 2; dz++) {
                    BlockPos position = new BlockPos(
                            canonicalX(originX + dx, geometry), Y + dy, z + dz);
                    level.getChunkAt(position);
                    level.setBlock(position, tank, Block.UPDATE_ALL);
                    positions.add(position);
                }
            }
        }
        BlockPos origin = new BlockPos(canonicalX(originX, geometry), Y, z);
        BlockEntity controllerEntity = multiBlockEntity(level, origin, label);
        formMulti(controllerEntity);
        IMultiBlockEntityContainer controller = multi(controllerEntity, label);
        if (controller.getWidth() != 2 || controller.getHeight() != 2) {
            throw new IllegalStateException(label + " expected native 2x2x2 dimensions, observed "
                    + controller.getWidth() + "x" + controller.getHeight());
        }
        for (BlockPos position : positions) {
            IMultiBlockEntityContainer part = multiBlock(level, position, label);
            requireCanonical(part.getController(), circumference, label + " controller");
            if (!part.getController().equals(origin)) {
                throw new IllegalStateException(label + " chose unexpected controller "
                        + part.getController() + " instead of " + origin);
            }
        }
        if (seam && positions.stream().noneMatch(position -> position.getX() == 0)) {
            throw new IllegalStateException("seam tank did not include canonical X=0");
        }

        BlockEntity persisted = positions.stream()
                .map(position -> multiBlockEntity(level, position, label))
                .filter(part -> !multi(part, label).isController()).findFirst().orElseThrow();
        verifyControllerReadAndWriteRepair(level, geometry, persisted,
                multi(persisted, label).getController(), circumference, label);
    }

    private static void verifyVaultNegativeControl(ServerLevel level, int x, int z) {
        BlockState vault = block("item_vault").defaultBlockState();
        if (vault.hasProperty(BlockStateProperties.HORIZONTAL_AXIS)) {
            vault = vault.setValue(BlockStateProperties.HORIZONTAL_AXIS, Direction.Axis.X);
        }
        BlockPos first = new BlockPos(x, Y, z);
        BlockPos second = first.east();
        clear(level, x, z);
        clear(level, x + 1, z);
        level.getChunkAt(first);
        level.setBlock(first, vault, Block.UPDATE_ALL);
        level.setBlock(second, vault, Block.UPDATE_ALL);
        BlockEntity controller = level.getBlockEntity(first);
        if (!(controller instanceof IMultiBlockEntityContainer)) {
            throw new IllegalStateException("vault negative control missing first block entity");
        }
        formMulti(controller);
        IMultiBlockEntityContainer formed = multiBlock(level, first, "vault negative control");
        IMultiBlockEntityContainer part = multiBlock(level, second, "vault negative control");
        if (formed.getWidth() != 1 || formed.getHeight() != 2
                || !part.getController().equals(first)) {
            throw new IllegalStateException("vault negative control changed Create-native connectivity");
        }
    }

    private static void verifyControllerReadAndWriteRepair(
            ServerLevel level, RingGeometry geometry, BlockEntity attached,
            BlockPos canonicalController, int circumference, String label) {
        requireCanonical(canonicalController, circumference, label + " initial controller");
        BlockPos alias = canonicalController.offset(circumference, 0, 0);

        CompoundTag baselineTag = attached.saveWithFullMetadata(level.registryAccess());
        CompoundTag aliasTag = baselineTag.copy();
        aliasTag.put("Controller", NbtUtils.writeBlockPos(alias));

        BlockEntity attachedClone = cloneOf(attached, label + " attached clone");
        attachedClone.setLevel(level);
        attachedClone.loadWithComponents(aliasTag, level.registryAccess());
        requireController(attachedClone, canonicalController, label + " attached read repair");
        requireCanonicalControllerTag(attachedClone.saveWithFullMetadata(level.registryAccess()),
                circumference, label + " attached saved NBT");

        BlockEntity loadContextClone = cloneOf(attached, label + " load-context clone");
        RingBlockEntityLoadContext.withGeometry(geometry, () -> {
            loadContextClone.loadWithComponents(aliasTag, level.registryAccess());
            requireController(loadContextClone, canonicalController,
                    label + " load-context read repair");
        });

        BlockEntity detached = cloneOf(attached, label + " deferred clone");
        detached.loadWithComponents(aliasTag, level.registryAccess());
        requireController(detached, alias, label + " second deferred read");
        detached.setLevel(level);
        CompoundTag repairedWrite = detached.saveWithFullMetadata(level.registryAccess());
        requireCanonicalControllerTag(repairedWrite, circumference,
                label + " defensive write repair");
        requireController(detached, canonicalController, label + " defensive write owner");
    }

    private static BlockEntity cloneOf(BlockEntity source, String label) {
        BlockEntity clone = source.getType().create(source.getBlockPos(), source.getBlockState());
        if (clone == null) throw new IllegalStateException(label + " was not created");
        return clone;
    }

    private static void requireController(BlockEntity entity, BlockPos expected, String label) {
        BlockPos actual = controller(entity, label);
        if (!expected.equals(actual)) {
            throw new IllegalStateException(label + " expected " + expected + ", observed " + actual);
        }
    }

    private static BlockPos controller(BlockEntity entity, String label) {
        if (entity instanceof RingCreate610BeltAccess belt) return belt.getController();
        if (entity instanceof IMultiBlockEntityContainer multi) return multi.getController();
        throw new IllegalStateException(label + " has unsupported block entity " + entity.getClass());
    }

    private static void requireCanonicalControllerTag(
            CompoundTag tag, int circumference, String label) {
        BlockPos controller = NbtUtils.readBlockPos(tag, "Controller")
                .orElseThrow(() -> new IllegalStateException(label + " has no Controller tag"));
        requireCanonical(controller, circumference, label);
    }

    private static BlockEntity multiBlockEntity(ServerLevel level, BlockPos position, String label) {
        BlockEntity entity = level.getBlockEntity(position);
        if (entity instanceof IMultiBlockEntityContainer) return entity;
        throw new IllegalStateException(label + " missing multiblock entity at " + position);
    }

    private static IMultiBlockEntityContainer multiBlock(
            ServerLevel level, BlockPos position, String label) {
        return multi(multiBlockEntity(level, position, label), label);
    }

    private static IMultiBlockEntityContainer multi(BlockEntity entity, String label) {
        if (entity instanceof IMultiBlockEntityContainer multi) return multi;
        throw new IllegalStateException(label + " is not a Create multiblock entity: " + entity);
    }

    private static <T extends BlockEntity & IMultiBlockEntityContainer> void formMultiTyped(T entity) {
        ConnectivityHandler.formMulti(entity);
    }

    @SuppressWarnings("unchecked")
    private static void formMulti(BlockEntity entity) {
        formMultiTyped((BlockEntity & IMultiBlockEntityContainer) entity);
    }

    private static void requireCanonical(BlockPos position, int circumference, String label) {
        if (position == null || position.getX() < 0 || position.getX() >= circumference) {
            throw new IllegalStateException(label + " is not canonical: " + position);
        }
    }

    private static Block block(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("create", path);
        Block block = BuiltInRegistries.BLOCK.get(id);
        if (block == Blocks.AIR) throw new IllegalStateException("missing Create block " + id);
        return block;
    }

    private static void clear(ServerLevel level, int x, int z) {
        for (int y = Y - 1; y <= Y + 2; y++) {
            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static int canonicalX(int x, RingGeometry geometry) {
        return RingBlockCoordinates.canonicalBlockX(x, geometry);
    }
}
