package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the local player's replicated bed position in the current client chart.
 *
 * <p>The server synchronizes and saves {@link LivingEntity#getSleepingPos()} in
 * canonical coordinates. Vanilla's client-side sleeping-data callback immediately
 * feeds that value to its private bed-position setter. At a presentation seam that
 * would place the local client player one full circumference away from the visible
 * bed. Mapping only this client getter preserves canonical server ownership while
 * making the vanilla set-position, orientation, wake, and bed-existence paths use
 * the nearby presentation copy.</p>
 */
@Mixin(LivingEntity.class)
abstract class LivingEntitySleepingPositionMixin {
    @Inject(method = "getSleepingPos", at = @At("RETURN"), cancellable = true)
    private void ringworld$projectLocalSleepingPosition(CallbackInfoReturnable<Optional<BlockPos>> cir) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != (Object) this) return;

        RingGeometry geometry = ClientRingState.geometry();
        Optional<BlockPos> canonical = cir.getReturnValue();
        if (geometry == null || canonical.isEmpty()) return;

        BlockPos bed = canonical.get();
        double presentationX = geometry.nearestImageX(bed.getX(), client.player.getX());
        if (presentationX == bed.getX()) return;

        cir.setReturnValue(Optional.of(new BlockPos(
                (int) Math.round(presentationX), bed.getY(), bed.getZ())));
    }
}
