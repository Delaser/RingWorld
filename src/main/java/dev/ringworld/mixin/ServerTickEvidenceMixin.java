package dev.ringworld.mixin;

import dev.ringworld.RingWorldMod;
import dev.ringworld.server.RingServerTickEvidence;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Measures outside tickServer so loader tail callbacks cannot escape the budget. */
@Mixin(MinecraftServer.class)
abstract class ServerTickEvidenceMixin {
    @Unique private static final boolean ringworld$measure = Boolean.getBoolean("ringworld.measureServerTicks");
    @Unique private RingServerTickEvidence ringworld$evidence;
    @Unique private long ringworld$shutdownStarted;
    @Shadow public abstract void tickServer(BooleanSupplier hasTime);

    @Redirect(method = "runServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;tickServer(Ljava/util/function/BooleanSupplier;)V"))
    private void ringworld$measureCompleteTick(MinecraftServer server, BooleanSupplier hasTime) {
        if (!ringworld$measure) { tickServer(hasTime); return; }
        if (ringworld$evidence == null) ringworld$evidence = new RingServerTickEvidence();
        ringworld$evidence.begin();
        try { tickServer(hasTime); }
        finally { ringworld$evidence.gameFinished(); }
    }

    @Inject(method = "runServer", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/MinecraftServer;waitUntilNextTick()V", shift = At.Shift.AFTER))
    private void ringworld$finishWorkWindow(CallbackInfo ci) {
        if (ringworld$evidence != null) ringworld$evidence.finish((MinecraftServer)(Object)this);
    }
    @Inject(method = "pollTaskInternal", at = @At("HEAD"))
    private void ringworld$enterTask(CallbackInfoReturnable<Boolean> cir) {
        if (ringworld$evidence != null) ringworld$evidence.taskStarted();
    }
    @Inject(method = "pollTaskInternal", at = @At("RETURN"))
    private void ringworld$leaveTask(CallbackInfoReturnable<Boolean> cir) {
        if (ringworld$evidence != null) ringworld$evidence.taskFinished();
    }
    @Inject(method = "waitForTasks", at = @At("HEAD"))
    private void ringworld$enterIdle(CallbackInfo ci) {
        if (ringworld$evidence != null) ringworld$evidence.idleStarted();
    }
    @Inject(method = "waitForTasks", at = @At("RETURN"))
    private void ringworld$leaveIdle(CallbackInfo ci) {
        if (ringworld$evidence != null) ringworld$evidence.idleFinished();
    }
    @Inject(method = "stopServer", at = @At("HEAD"))
    private void ringworld$startShutdown(CallbackInfo ci) {
        if (ringworld$evidence != null) ringworld$shutdownStarted = System.nanoTime();
    }
    @Inject(method = "stopServer", at = @At("TAIL"))
    private void ringworld$export(CallbackInfo ci) {
        if (ringworld$evidence != null) ringworld$evidence.write((MinecraftServer)(Object)this,
                System.nanoTime() - ringworld$shutdownStarted);
    }

    /** Deliberate separate diagnostic: vanilla's stored tick time precedes this hook. */
    @Inject(method = "tickServer", at = @At("TAIL"))
    private void ringworld$proveLateHookMeasurement(BooleanSupplier hasTime, CallbackInfo ci) {
        var server = (MinecraftServer)(Object)this;
        if (!Boolean.getBoolean("ringworld.testTickEvidence") || server.getTickCount() != 40) return;
        if (!ringworld$measure) throw new IllegalStateException("Late-hook probe requires inclusive measurements");
        long started = System.nanoTime();
        long remaining;
        while ((remaining = 100_000_000L - (System.nanoTime() - started)) > 0) {
            LockSupport.parkNanos(remaining);
        }
        RingWorldMod.LOGGER.info("[server-tick-probe] tick=40 lateHookNanos={} vanillaNanos={}",
                System.nanoTime() - started, server.getTickTimesNanos()[40 % 100]);
    }
}
