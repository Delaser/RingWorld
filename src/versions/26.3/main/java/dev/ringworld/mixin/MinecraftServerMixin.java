package dev.ringworld.mixin;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSpawnBounds;
import dev.ringworld.world.RingWorldConfig;
import dev.ringworld.world.RingWorldStorageAccess;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Owns first-world spawn bounds and the read-only dimension-storage bridge. */
@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin implements RingWorldStorageAccess {
    @Shadow @Final protected LevelStorageSource.LevelStorageAccess storageSource;

    @Override
    public Path ringworld$getDimensionPath(ResourceKey<Level> dimension) {
        return storageSource.getDimensionPath(dimension);
    }

    @Redirect(method = "setInitialSpawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/chunk/ChunkGenerator;getOrigin(Lnet/minecraft/world/level/levelgen/RandomState;)Lnet/minecraft/world/level/ChunkPos;"))
    private static net.minecraft.world.level.ChunkPos ringworld$constrainInitialSpawn(
            net.minecraft.world.level.chunk.ChunkGenerator generator,
            net.minecraft.world.level.levelgen.RandomState state) {
        var vanilla = generator.getOrigin(state);
        RingWorldConfig config = RingWorldConfig.load();
        RingGeometry geometry = new RingGeometry(config.widthBlocks(), config.circumferenceBlocks());
        return new net.minecraft.world.level.ChunkPos(Math.floorMod(vanilla.x(), geometry.circumferenceChunks()),
                Math.floorDiv(RingSpawnBounds.constrainInitialSpawnZ(vanilla.z() * 16 + 8, geometry), 16));
    }

    @Redirect(
            method = "setInitialSpawn",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/storage/LevelData$RespawnData;of(Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/core/BlockPos;FF)Lnet/minecraft/world/level/storage/LevelData$RespawnData;"),
            require = 3)
    private static LevelData.RespawnData ringworld$canonicalInitialRespawnData(
            ResourceKey<Level> dimension, BlockPos position, float yaw, float pitch) {
        if (dimension != Level.OVERWORLD) {
            return LevelData.RespawnData.of(dimension, position, yaw, pitch);
        }
        RingWorldConfig config = RingWorldConfig.load();
        RingGeometry geometry = new RingGeometry(config.widthBlocks(), config.circumferenceBlocks());
        return LevelData.RespawnData.of(dimension,
                RingSpawnBounds.canonicalInitialSpawn(position, geometry), yaw, pitch);
    }
}
