package dev.ringworld.mixin;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Narrow access used only by the opt-in deterministic seam-raid fixture. */
@Mixin(Raid.class)
public interface RaidFixtureAccessor {
    @Accessor("raidCooldownTicks")
    void ringworld$setFixtureRaidCooldownTicks(int ticks);

    @Accessor("groupsSpawned")
    void ringworld$setFixtureGroupsSpawned(int groupsSpawned);

    @Accessor("raidEvent")
    ServerBossEvent ringworld$fixtureBossEvent();
}
