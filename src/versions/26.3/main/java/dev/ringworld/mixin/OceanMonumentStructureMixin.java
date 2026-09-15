package dev.ringworld.mixin;
import org.spongepowered.asm.mixin.Mixin;
/** 26.3 biome resolvers already use the configured periodic RandomState. */
@Mixin(net.minecraft.world.level.levelgen.structure.structures.OceanMonumentStructure.class)
abstract class OceanMonumentStructureMixin { }
