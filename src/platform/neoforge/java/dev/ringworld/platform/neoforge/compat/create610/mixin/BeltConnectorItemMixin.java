package dev.ringworld.platform.neoforge.compat.create610.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ringworld.platform.neoforge.compat.create610.RingCreate610ServerCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keeps Create's belt relationship math local while preserving canonical ownership. */
@Pseudo
@Mixin(targets = "com.simibubi.create.content.kinetics.belt.item.BeltConnectorItem", remap = false)
abstract class BeltConnectorItemMixin {
    private static final String CAN_CONNECT =
            "canConnect(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/core/BlockPos;)Z";
    private static final String CREATE_BELTS =
            "createBelts(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
                    + "Lnet/minecraft/core/BlockPos;)V";

    @WrapOperation(
            method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)"
                    + "Lnet/minecraft/world/InteractionResult;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/core/BlockPos;closerThan(Lnet/minecraft/core/Vec3i;D)Z"),
            require = 1, allow = 1)
    private boolean ringworld$nearestStoredEndpointComparison(
            BlockPos first, Vec3i target, double distance, Operation<Boolean> original,
            @Local(argsOnly = true) UseOnContext context) {
        BlockPos targetPosition = (BlockPos) target;
        BlockPos image = RingCreate610ServerCoordinates.nearestRelationshipPosition(
                context.getLevel(), targetPosition, first.getX());
        return original.call(first, image, distance);
    }

    @ModifyVariable(method = CAN_CONNECT, at = @At("HEAD"), argsOnly = true,
            ordinal = 1, require = 1)
    private static BlockPos ringworld$nearestValidationEndpoint(
            BlockPos second,
            @Local(argsOnly = true) Level world,
            @Local(argsOnly = true, ordinal = 0) BlockPos first) {
        return RingCreate610ServerCoordinates.nearestRelationshipPosition(
                world, second, first.getX());
    }

    @WrapOperation(method = CAN_CONNECT,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;isLoaded(Lnet/minecraft/core/BlockPos;)Z"),
            require = 2, allow = 2)
    private static boolean ringworld$canonicalLoadedCheck(
            Level world, BlockPos position, Operation<Boolean> original) {
        return original.call(world,
                RingCreate610ServerCoordinates.canonicalLevelPosition(world, position));
    }

    @WrapOperation(method = CAN_CONNECT,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)"
                            + "Lnet/minecraft/world/level/block/state/BlockState;"),
            require = 3, allow = 3)
    private static BlockState ringworld$canonicalValidationBlockState(
            Level world, BlockPos position, Operation<BlockState> original) {
        return original.call(world,
                RingCreate610ServerCoordinates.canonicalLevelPosition(world, position));
    }

    @WrapOperation(method = CAN_CONNECT,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)"
                            + "Lnet/minecraft/world/level/block/entity/BlockEntity;"),
            require = 2, allow = 2)
    private static BlockEntity ringworld$canonicalValidationBlockEntity(
            Level world, BlockPos position, Operation<BlockEntity> original) {
        return original.call(world,
                RingCreate610ServerCoordinates.canonicalLevelPosition(world, position));
    }

    @ModifyVariable(method = CREATE_BELTS, at = @At("HEAD"), argsOnly = true,
            ordinal = 1, require = 1)
    private static BlockPos ringworld$nearestCreationEndpoint(
            BlockPos end,
            @Local(argsOnly = true) Level world,
            @Local(argsOnly = true, ordinal = 0) BlockPos start) {
        return RingCreate610ServerCoordinates.nearestRelationshipPosition(
                world, end, start.getX());
    }

    @WrapOperation(method = CREATE_BELTS,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;playSound("
                            + "Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/sounds/SoundEvent;Lnet/minecraft/sounds/SoundSource;FF)V"),
            require = 1, allow = 1)
    private static void ringworld$canonicalCreationSound(
            Level world, Player player, BlockPos position, SoundEvent sound,
            SoundSource source, float volume, float pitch, Operation<Void> original) {
        original.call(world, player,
                RingCreate610ServerCoordinates.canonicalLevelPosition(world, position),
                sound, source, volume, pitch);
    }

    @WrapOperation(method = CREATE_BELTS,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)"
                            + "Lnet/minecraft/world/level/block/state/BlockState;"),
            require = 4, allow = 4)
    private static BlockState ringworld$canonicalCreationBlockState(
            Level world, BlockPos position, Operation<BlockState> original) {
        return original.call(world,
                RingCreate610ServerCoordinates.canonicalLevelPosition(world, position));
    }

    @WrapOperation(method = CREATE_BELTS,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;getDestroySpeed("
                            + "Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)F"),
            require = 1, allow = 1)
    private static float ringworld$canonicalDestroySpeedPosition(
            BlockState state, BlockGetter world, BlockPos position, Operation<Float> original) {
        BlockPos canonical = world instanceof Level level
                ? RingCreate610ServerCoordinates.canonicalLevelPosition(level, position)
                : position;
        return original.call(state, world, canonical);
    }

    @WrapOperation(method = CREATE_BELTS,
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;destroyBlock(Lnet/minecraft/core/BlockPos;Z)Z"),
            require = 2, allow = 2)
    private static boolean ringworld$canonicalDestroyPosition(
            Level world, BlockPos position, boolean drop, Operation<Boolean> original) {
        return original.call(world,
                RingCreate610ServerCoordinates.canonicalLevelPosition(world, position), drop);
    }

    @WrapOperation(method = CREATE_BELTS,
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/block/ProperWaterloggedBlock;withWater("
                            + "Lnet/minecraft/world/level/LevelAccessor;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)"
                            + "Lnet/minecraft/world/level/block/state/BlockState;"),
            require = 1, allow = 1)
    private static BlockState ringworld$canonicalWaterloggedPosition(
            LevelAccessor world, BlockState state, BlockPos position,
            Operation<BlockState> original) {
        BlockPos canonical = world instanceof Level level
                ? RingCreate610ServerCoordinates.canonicalLevelPosition(level, position)
                : position;
        return original.call(world, state, canonical);
    }

    @WrapOperation(method = CREATE_BELTS,
            at = @At(value = "INVOKE",
                    target = "Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;"
                            + "switchToBlockState(Lnet/minecraft/world/level/Level;"
                            + "Lnet/minecraft/core/BlockPos;"
                            + "Lnet/minecraft/world/level/block/state/BlockState;)V"),
            require = 1, allow = 1)
    private static void ringworld$canonicalBeltPlacement(
            Level world, BlockPos position, BlockState state, Operation<Void> original) {
        original.call(world,
                RingCreate610ServerCoordinates.canonicalLevelPosition(world, position), state);
    }
}
