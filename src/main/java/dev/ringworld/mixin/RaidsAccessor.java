package dev.ringworld.mixin;

import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raids;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Exposes the saved raid collection for periodic nearest-raid selection. */
@Mixin(Raids.class)
public interface RaidsAccessor {
    @Accessor("raidMap")
    Map<Integer, Raid> ringworld$getRaidMap();
}
