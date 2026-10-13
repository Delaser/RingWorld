package dev.ringworld.client.compat;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/** Optional renderer adapters do not load foreign classes during discovery. */
public final class RingOptionalRenderMixins implements IMixinConfigPlugin {
    public void onLoad(String mixinPackage) { }
    public String getRefMapperConfig() { return null; }
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (!mixinClassName.contains(".compat.")) return true;
        var loader = getClass().getClassLoader();
        return loader.getResource("net/caffeinemc/mods/sodium/client/SodiumClientMod.class") != null
                && loader.getResource(targetClassName.replace('.', '/') + ".class") != null;
    }
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }
    public List<String> getMixins() { return null; }
    public void preApply(String name, ClassNode node, String mixin, IMixinInfo info) { }
    public void postApply(String name, ClassNode node, String mixin, IMixinInfo info) { }
}
