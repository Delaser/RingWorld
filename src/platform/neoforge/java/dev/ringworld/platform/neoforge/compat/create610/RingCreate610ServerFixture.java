package dev.ringworld.platform.neoforge.compat.create610;

import com.simibubi.create.api.connectivity.ConnectivityHandler;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.kinetics.belt.BeltBlock;
import com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** Bounded server-only seam formation and controller-persistence fixture. */
final class RingCreate610ServerFixture {
    private static final int Y = 120;
    static final int FORWARD_CLICK_Z = 76;
    static final int REVERSE_CLICK_Z = 77;
    static final int SEAM_BELT_Z = 80;
    static final int SEAM_TANK_Z = 92;
    static final int BASELINE_TANK_Z = 100;
    private static final int DURABLE_FLUID_AMOUNT = 3_000;

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

    static QualificationSetup prepareClientQualification(
            ServerLevel level, ServerPlayer player) {
        verify(level);
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        int circumference = geometry.circumferenceBlocks();
        prepareShaftPair(level, geometry, circumference - 3, 1, FORWARD_CLICK_Z);
        prepareShaftPair(level, geometry, 1, circumference - 3, REVERSE_CLICK_Z);
        for (int z = FORWARD_CLICK_Z - 1; z <= REVERSE_CLICK_Z + 1; z++) {
            level.setBlock(new BlockPos(circumference - 1, Y - 1, z),
                    Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }

        player.getInventory().selected = 0;
        player.getInventory().setItem(0, new ItemStack(item("belt_connector"), 8));

        BlockPos motorPosition = new BlockPos(circumference - 3, Y, SEAM_BELT_Z + 1);
        level.setBlock(motorPosition, block("creative_motor").defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.NORTH), Block.UPDATE_ALL);
        RingCreate610BeltAccess belt = beltController(level,
                new BlockPos(circumference - 3, Y, SEAM_BELT_Z), "durable seam belt");
        TransportedItemStack transported = new TransportedItemStack(
                new ItemStack(Items.DIAMOND, 3));
        transported.beltPosition = transported.prevBeltPosition = 0.5F;
        belt.getInventory().addItem(transported);
        markUpdated(level, (BlockEntity) belt);

        RingCreate610TankAccess seamTank = tankController(level,
                new BlockPos(circumference - 1, Y, SEAM_TANK_Z), "durable seam tank");
        RingCreate610TankAccess baselineTank = tankController(level,
                new BlockPos(24, Y, BASELINE_TANK_Z), "durable baseline tank");
        int seamCapacity = seamTank.ringworld$tankInventory().getTankCapacity(0);
        int baselineCapacity = baselineTank.ringworld$tankInventory().getTankCapacity(0);
        if (seamCapacity != baselineCapacity || seamCapacity <= DURABLE_FLUID_AMOUNT) {
            throw new IllegalStateException("seam tank capacity " + seamCapacity
                    + " differs from baseline " + baselineCapacity);
        }
        fillExactly(seamTank, DURABLE_FLUID_AMOUNT, "durable seam tank");
        fillExactly(baselineTank, DURABLE_FLUID_AMOUNT, "durable baseline tank");
        RingWorldMod.LOGGER.info(
                "[create-compat-client] setup connector=real-clicks tankCapacity={} "
                        + "tankFluid={} beltItem=3xdiamond",
                seamCapacity, DURABLE_FLUID_AMOUNT);
        return new QualificationSetup(seamCapacity, DURABLE_FLUID_AMOUNT);
    }

    static boolean freezeTransferredItemAfterSeam(ServerLevel level) {
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        RingCreate610BeltAccess belt = beltController(level,
                new BlockPos(geometry.circumferenceBlocks() - 3, Y, SEAM_BELT_Z),
                "durable seam belt");
        TransportedItemStack item = belt.getInventory().getTransportedItems().stream()
                .filter(candidate -> candidate.stack.is(Items.DIAMOND))
                .findFirst().orElse(null);
        if (item == null || item.beltPosition < 3.0F) return false;
        level.setBlock(new BlockPos(geometry.circumferenceBlocks() - 3,
                Y, SEAM_BELT_Z + 1), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        belt.setSpeed(0.0F);
        markUpdated(level, (BlockEntity) belt);
        RingWorldMod.LOGGER.info(
                "[create-compat-client] belt item crossed canonical seam position={}",
                item.beltPosition);
        return true;
    }

    static DurableReload verifyDurableReload(ServerLevel level) {
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        int circumference = geometry.circumferenceBlocks();
        RingCreate610BeltAccess belt = beltController(level,
                new BlockPos(circumference - 3, Y, SEAM_BELT_Z), "reloaded seam belt");
        requireCanonicalControllerTag(((BlockEntity) belt).saveWithFullMetadata(level.registryAccess()),
                circumference, "reloaded belt raw NBT");
        TransportedItemStack item = belt.getInventory().getTransportedItems().stream()
                .filter(candidate -> candidate.stack.is(Items.DIAMOND)
                        && candidate.stack.getCount() == 3 && candidate.beltPosition >= 3.0F)
                .findFirst().orElseThrow(() -> new IllegalStateException(
                        "reloaded seam belt lost its transferred item"));

        RingCreate610TankAccess seamTank = tankController(level,
                new BlockPos(circumference - 1, Y, SEAM_TANK_Z), "reloaded seam tank");
        RingCreate610TankAccess baselineTank = tankController(level,
                new BlockPos(24, Y, BASELINE_TANK_Z), "reloaded baseline tank");
        BlockEntity seamTankPart = level.getBlockEntity(new BlockPos(0, Y, SEAM_TANK_Z));
        if (seamTankPart == null) throw new IllegalStateException("reloaded seam tank part missing");
        requireCanonicalControllerTag(seamTankPart.saveWithFullMetadata(level.registryAccess()),
                circumference, "reloaded tank raw NBT");
        int seamCapacity = seamTank.ringworld$tankInventory().getTankCapacity(0);
        int baselineCapacity = baselineTank.ringworld$tankInventory().getTankCapacity(0);
        int seamFluid = seamTank.ringworld$tankInventory().getFluidInTank(0).getAmount();
        int baselineFluid = baselineTank.ringworld$tankInventory().getFluidInTank(0).getAmount();
        if (seamCapacity != baselineCapacity || seamFluid != DURABLE_FLUID_AMOUNT
                || baselineFluid != DURABLE_FLUID_AMOUNT) {
            throw new IllegalStateException("reloaded tank mismatch capacity=" + seamCapacity
                    + "/" + baselineCapacity + " fluid=" + seamFluid + "/" + baselineFluid);
        }
        RingWorldMod.LOGGER.info(
                "[create-compat-client] durable reload canonicalNbt=true beltItemPosition={} "
                        + "tankCapacity={} tankFluid={}",
                item.beltPosition, seamCapacity, seamFluid);
        return new DurableReload(item.beltPosition, seamCapacity, seamFluid);
    }

    static void verifyClickedBelt(ServerLevel level, int z, String label) {
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        int circumference = geometry.circumferenceBlocks();
        for (int x : new int[] {circumference - 3, circumference - 2,
                circumference - 1, 0, 1}) {
            BlockEntity entity = level.getBlockEntity(new BlockPos(x, Y, z));
            if (!(entity instanceof RingCreate610BeltAccess access)) {
                throw new IllegalStateException(label + " missing belt at x=" + x);
            }
            requireCanonical(access.getController(), circumference, label + " controller");
        }
    }

    static String describeClickedBeltState(ServerLevel level, ServerPlayer player, int z) {
        RingGeometry geometry = RingWorldServer.geometryFor(level);
        int circumference = geometry.circumferenceBlocks();
        BlockPos first = new BlockPos(circumference - 3, Y, z);
        BlockPos second = new BlockPos(1, Y, z);
        List<String> blocks = new ArrayList<>();
        for (int x : new int[] {circumference - 3, circumference - 2,
                circumference - 1, 0, 1}) {
            BlockPos position = new BlockPos(x, Y, z);
            BlockEntity blockEntity = level.getBlockEntity(position);
            blocks.add(x + "=" + level.getBlockState(position).getBlock()
                    + "/" + (blockEntity == null ? "none" : blockEntity.getClass().getSimpleName()));
        }
        return "player=" + player.position()
                + " canReachFirst=" + player.canInteractWithBlock(first, 1.0)
                + " canReachSecond=" + player.canInteractWithBlock(second, 1.0)
                + " mayFirst=" + level.mayInteract(player, first)
                + " maySecond=" + level.mayInteract(player, second)
                + " canConnect=" + BeltConnectorItem.canConnect(level, first, second)
                + " cooldown=" + player.getCooldowns().isOnCooldown(
                        player.getMainHandItem().getItem())
                + " held=" + player.getMainHandItem().getComponents() + " blocks=" + blocks;
    }

    static boolean connectorSecondClickReady(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();
        return stack.has(AllDataComponents.BELT_FIRST_SHAFT)
                && !player.getCooldowns().isOnCooldown(stack.getItem());
    }

    private static void prepareShaftPair(ServerLevel level, RingGeometry geometry,
                                         int startX, int endX, int z) {
        for (int x = -4; x <= 4; x++) {
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
    }

    private static RingCreate610BeltAccess beltController(
            ServerLevel level, BlockPos position, String label) {
        BlockEntity entity = level.getBlockEntity(position);
        if (!(entity instanceof RingCreate610BeltAccess belt)) {
            throw new IllegalStateException(label + " missing at " + position);
        }
        BlockEntity controller = level.getBlockEntity(belt.getController());
        if (!(controller instanceof RingCreate610BeltAccess result)) {
            throw new IllegalStateException(label + " controller missing at " + belt.getController());
        }
        return result;
    }

    private static RingCreate610TankAccess tankController(
            ServerLevel level, BlockPos position, String label) {
        BlockEntity entity = level.getBlockEntity(position);
        if (!(entity instanceof RingCreate610TankAccess)
                || !(entity instanceof IMultiBlockEntityContainer tank)) {
            throw new IllegalStateException(label + " missing at " + position);
        }
        BlockEntity controller = level.getBlockEntity(tank.getController());
        if (!(controller instanceof RingCreate610TankAccess result)) {
            throw new IllegalStateException(label + " controller missing at " + tank.getController());
        }
        return result;
    }

    private static void fillExactly(RingCreate610TankAccess tank, int amount, String label) {
        var inventory = tank.ringworld$tankInventory();
        inventory.drain(Integer.MAX_VALUE, FluidAction.EXECUTE);
        int filled = inventory.fill(
                new FluidStack(Fluids.WATER, amount), FluidAction.EXECUTE);
        if (filled != amount) throw new IllegalStateException(label + " filled only " + filled);
        BlockEntity entity = (BlockEntity) tank;
        markUpdated((ServerLevel) entity.getLevel(), entity);
    }

    private static void markUpdated(ServerLevel level, BlockEntity entity) {
        entity.setChanged();
        level.sendBlockUpdated(entity.getBlockPos(), entity.getBlockState(),
                entity.getBlockState(), Block.UPDATE_ALL);
    }

    record QualificationSetup(int tankCapacity, int tankFluid) { }
    record DurableReload(float beltItemPosition, int tankCapacity, int tankFluid) { }

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

    private static Item item(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("create", path);
        Item item = BuiltInRegistries.ITEM.get(id);
        if (item == Items.AIR) throw new IllegalStateException("missing Create item " + id);
        return item;
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
