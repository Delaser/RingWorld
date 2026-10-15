package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.RingGeometry;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/** Opt-in disposable two-client #254 regression, independent of the full seam suite. */
final class RingOffRingVisibilityTest {
    private static final Map<String, Boolean> results = new HashMap<>();
    private static int stage;
    private static int ticks;
    private static boolean armed;
    private static Entity cow;
    private static Entity boat;
    private static final TicketType<ChunkPos> FIXTURE_TICKET = TicketType.create(
            "ringworld_visibility_fixture", java.util.Comparator.comparingLong(ChunkPos::toLong));
    private static final Set<ChunkPos> heldChunks = new java.util.HashSet<>();

    static void record(String role, String phase, boolean passed) {
        results.put(role + ':' + phase, passed);
    }

    static void tick(ServerLevel world, RingGeometry geometry) {
        ServerPlayer a = player(world, "RingTesterA");
        ServerPlayer b = player(world, "RingTesterB");
        if (++ticks > 2400) {
            RingWorldMod.LOGGER.error("[off-ring] result=false timeout stage={} results={}", stage, results);
            world.getServer().halt(false);
            return;
        }
        if (a == null || b == null || !passed("A", "client_ready")
                || !passed("B", "client_ready")) return;
        if (!armed) {
            double x = stage == 5 ? geometry.circumferenceBlocks() - 0.5 : 64.5 + stage * 16;
            double z = stage == 2 || stage == 6 ? geometry.minWidthZ() - 8.5
                    : stage == 1 ? 0.5 : geometry.maxWidthZ() + 9.5;
            double bz = stage == 2 || stage == 6 ? geometry.minWidthZ() + 8.5
                    : stage == 1 ? 0.5 : geometry.maxWidthZ() - 8.5;
            // Own the native fixture entities until explicit removal. A long
            // observer teleport must not unload/save them behind stale Java references.
            retire(world, cow);
            retire(world, boat);
            for (ChunkPos held : heldChunks) world.getChunkSource().removeRegionTicket(FIXTURE_TICKET, held, 0, held);
            heldChunks.clear();
            // Explicit fixture setup only; fixture terrain remains empty and server-authoritative.
            world.getChunk((int)x >> 4, (int)z >> 4);
            a.setGameMode(GameType.CREATIVE);
            b.setGameMode(GameType.CREATIVE);
            a.setNoGravity(stage != 6);
            b.setNoGravity(true);
            a.getAbilities().flying = stage != 6;
            b.getAbilities().flying = true;
            a.onUpdateAbilities();
            b.onUpdateAbilities();
            a.setDeltaMovement(Vec3.ZERO);
            b.setDeltaMovement(Vec3.ZERO);
            a.teleportTo(world, x, stage == 6 ? 310 : 180, z, Set.<RelativeMovement>of(), 180, 0);
            double bx = stage == 3 ? x + 300 : geometry.wrapX(x + 6);
            b.teleportTo(world, bx, 180, bz, Set.<RelativeMovement>of(), 90, 0);
            cow = spawn(world, "cow", x - 3, z);
            boat = spawn(world, "oak_boat", x + 3, z);
            armed = true;
            ticks = 0;
            RingWorldMod.LOGGER.info("[off-ring] armed stage={} A={},{} B={},{}", stage, x, z, bx, bz);
        }
        if (stage == 7) {
            // Preserve ordinary survival void damage; never force damage in the test.
            if (ticks == 1) {
                a.setGameMode(GameType.SURVIVAL);
                a.setNoGravity(false);
                a.teleportTo(world, a.getX(), -100, a.getZ(), Set.<RelativeMovement>of(), 180, 0);
            }
            if (a.getHealth() < 20 || !a.isAlive()) {
                RingWorldMod.LOGGER.info("[off-ring] result=true all 7 visibility stages and vanilla void damage passed");
                world.getServer().halt(false);
            }
            return;
        }
        if (ticks % 100 == 0) {
            RingWorldMod.LOGGER.info("[off-ring] pending stage={} entities={}", stage,
                    java.util.stream.StreamSupport.stream(world.getAllEntities().spliterator(), false)
                            .filter(e -> e.getCustomName() != null && e.getCustomName().getString().startsWith("RingVisibility-"))
                            .map(e -> e.getId() + ":" + e.getUUID() + ":" + e.getRemovalReason() + ':' + e.position()).toList());
        }
        if (ticks > 40 && passed("A", "off_ring_" + stage) && passed("B", "off_ring_" + stage)) {
            RingWorldMod.LOGGER.info("[off-ring] stage={} result=true", stage);
            stage++;
            armed = false;
            ticks = 0;
        }
    }

    private static Entity spawn(ServerLevel world, String type, double x, double z) {
        ChunkPos position = new ChunkPos(net.minecraft.core.BlockPos.containing(x, 180, z));
        if (heldChunks.add(position)) world.getChunkSource().addRegionTicket(FIXTURE_TICKET, position, 0, position);
        world.getChunk(position.x, position.z);
        Entity entity = (type.equals("cow") ? net.minecraft.world.entity.EntityType.COW
                : net.minecraft.world.entity.EntityType.BOAT).create(world);
        if (entity == null) throw new IllegalStateException("Missing fixture " + type);
        entity.setPos(x, 180, z);
        entity.setNoGravity(true);
        if (entity instanceof net.minecraft.world.entity.Mob mob) mob.setNoAi(true);
        entity.setCustomName(Component.literal("RingVisibility-" + type));
        world.addFreshEntity(entity);
        return entity;
    }

    private static void retire(ServerLevel world, Entity entity) {
        if (entity == null) return;
        RingWorldMod.LOGGER.info("[off-ring] retiring id={} uuid={} reason={} currentSame={}",
                entity.getId(), entity.getUUID(), entity.getRemovalReason(), world.getEntity(entity.getUUID()) == entity);
        if (entity.isRemoved() || world.getEntity(entity.getUUID()) != entity) {
            throw new IllegalStateException("Visibility fixture lost its held entity before explicit removal");
        }
        entity.discard();
        if (world.getEntity(entity.getUUID()) != null) {
            throw new IllegalStateException("Visibility fixture entity remained indexed after explicit removal");
        }
    }

    private static boolean passed(String role, String phase) {
        return Boolean.TRUE.equals(results.get(role + ':' + phase));
    }

    private static ServerPlayer player(ServerLevel world, String name) {
        return world.players().stream().filter(p -> p.getName().getString().equals(name)).findFirst().orElse(null);
    }
}
