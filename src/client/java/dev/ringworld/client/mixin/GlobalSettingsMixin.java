package dev.ringworld.client.mixin;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingGeometry;
import net.minecraft.client.gl.GlobalSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Supplies the synchronized circumference to the terrain shader's Globals UBO. */
@Mixin(GlobalSettings.class)
abstract class GlobalSettingsMixin {
    /**
     * The third integer parameter is the menu blur radius. RingWorld packs a
     * negative activation marker, the circumference, and the original four-
     * bit blur value into it. Our terrain and box-blur shaders decode their
     * respective parts, so opening a menu cannot temporarily corrupt terrain.
     */
    @ModifyVariable(method = "set", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int ringworld$publishCircumference(int menuBlurRadius) {
        RingGeometry geometry = ClientRingState.geometry();
        if (geometry == null) return menuBlurRadius;
        return Integer.MIN_VALUE
                | (geometry.circumferenceBlocks() << 4)
                | (menuBlurRadius & 0xF);
    }
}
