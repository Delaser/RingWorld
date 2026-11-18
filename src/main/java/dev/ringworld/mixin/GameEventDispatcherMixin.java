package dev.ringworld.mixin;

import dev.ringworld.server.RingWorldServer;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventDispatcher;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import java.util.List;

@Mixin(GameEventDispatcher.class)
abstract class GameEventDispatcherMixin {
    @Shadow @Final private ServerLevel level;

    // Both immediate and distance-ordered delivery need the same nearby source for travel and occlusion.
    @ModifyVariable(method = "lambda$post$0", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private Vec3 ringworld$nearbySource(Vec3 source, List<GameEvent.ListenerInfo> queued, Holder<GameEvent> event,
            Vec3 original, GameEvent.Context context, GameEventListener listener, Vec3 listenerPos) {
        if (level.dimension() != Level.OVERWORLD) return source;
        double x = RingWorldServer.geometryFor(level).nearestImageX(source.x, listenerPos.x);
        return x == source.x ? source : new Vec3(x, source.y, source.z);
    }
}
