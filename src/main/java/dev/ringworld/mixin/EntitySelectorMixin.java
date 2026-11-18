package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingTopology;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(EntitySelector.class)
abstract class EntitySelectorMixin {
    // Vanilla applies its selector box a second time after collecting section candidates.
    @Redirect(method = "lambda$getPredicate$1", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/phys/AABB;intersects(Lnet/minecraft/world/phys/AABB;)Z"))
    private static boolean ringworld$selectorIntersects(AABB query, AABB bounds, AABB selector, Entity entity) {
        if (query.intersects(bounds)) return true;
        if (!(entity.level() instanceof ServerLevel world) || world.dimension() != Level.OVERWORLD) return false;
        var geometry = RingWorldServer.geometryFor(world);
        return RingTopology.needsEntityImages(query, geometry)
                && query.intersects(new RingTopology(geometry).projectBoxNear(bounds, query.getCenter().x));
    }
}
