package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import dev.ringworld.world.RingGeometry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes high-level entity distance checks through shortest ring distance. */
@Mixin(Entity.class)
abstract class EntityDistanceMixin {
    @Inject(method = "distanceToSqr(DDD)D", at = @At("HEAD"), cancellable = true)
    private void ringworld$squaredDistance(double x, double y, double z, CallbackInfoReturnable<Double> cir) {
        Entity self = (Entity) (Object) this;
        RingGeometry geometry = geometry(self);
        if (geometry == null) return;
        double dx = geometry.shortestCircumferenceDelta(x, self.getX());
        double dy = self.getY() - y;
        double dz = self.getZ() - z;
        cir.setReturnValue(dx * dx + dy * dy + dz * dz);
    }

    @Inject(method = "distanceToSqr(Lnet/minecraft/world/phys/Vec3;)D", at = @At("HEAD"), cancellable = true)
    private void ringworld$squaredDistance(Vec3 position, CallbackInfoReturnable<Double> cir) {
        ringworld$squaredDistance(position.x, position.y, position.z, cir);
    }

    @Inject(method = "distanceTo", at = @At("HEAD"), cancellable = true)
    private void ringworld$distance(Entity other, CallbackInfoReturnable<Float> cir) {
        Entity self = (Entity) (Object) this;
        RingGeometry geometry = geometry(self);
        if (geometry == null) return;
        double dx = geometry.shortestCircumferenceDelta(other.getX(), self.getX());
        double dy = self.getY() - other.getY();
        double dz = self.getZ() - other.getZ();
        cir.setReturnValue(Mth.sqrt((float) (dx * dx + dy * dy + dz * dz)));
    }

    private static RingGeometry geometry(Entity entity) {
        return entity.level() instanceof ServerLevel world && world.dimension() == Level.OVERWORLD
                ? RingWorldServer.geometryFor(world) : null;
    }
}
