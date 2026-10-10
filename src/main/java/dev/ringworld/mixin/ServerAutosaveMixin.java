package dev.ringworld.mixin;

import dev.ringworld.server.RingChunkWorkContext;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Scope only the scheduled autosave; commands, flushes and shutdown retain vanilla semantics. */
@Mixin(MinecraftServer.class)
abstract class ServerAutosaveMixin {
    @Redirect(method = "autoSave", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;saveEverything(ZZZ)Z"))
    private boolean ringworld$periodicSave(MinecraftServer server, boolean silent, boolean flush, boolean force) {
        return RingChunkWorkContext.periodicSave(() -> server.saveEverything(silent, flush, force));
    }
}
