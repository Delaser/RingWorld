package dev.ringworld.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Ownership-boundary access used before a server block entity is registered. */
@Mixin(BlockEntity.class)
public interface BlockEntityPositionAccessor {
    @Mutable
    @Accessor("worldPosition")
    void ringworld$setWorldPosition(BlockPos position);
}
