package dev.ringworld.experiment.mixin;

import net.minecraft.client.renderer.rendertype.*;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderType.class)
public interface RenderTypeAccess {
    @Invoker("create")
    static RenderType createType(String name, RenderSetup setup) {
        throw new AssertionError();
    }
}
