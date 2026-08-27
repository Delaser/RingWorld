package dev.ringworld.server;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Version-stable vanilla registry access for opt-in runtime-fixture materials
 * and entities whose generated {@code Blocks}/{@code EntityType} constants
 * were removed in 26.2.
 */
public final class RingWorldVanillaFixtureRegistries {
    private RingWorldVanillaFixtureRegistries() { }

    public static Block block(String path) {
        return BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace(path))
                .orElseThrow(() -> new IllegalStateException("Missing vanilla fixture block: " + path));
    }

    public static Item item(String path) {
        return BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(path))
                .orElseThrow(() -> new IllegalStateException("Missing vanilla fixture item: " + path));
    }

    /**
     * Creates a fixture entity and checks its actual factory result.  In 26.2
     * some vanilla registry entries, including {@code oak_boat}, intentionally
     * expose {@code Entity} as their type's base class even though their factory
     * still creates the specialized runtime entity.
     */
    public static <T extends Entity> T createEntity(String path, Class<T> expectedClass,
                                                     Level level, EntitySpawnReason reason) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE
                .getOptional(Identifier.withDefaultNamespace(path))
                .orElseThrow(() -> new IllegalStateException("Missing vanilla fixture entity type: " + path));
        Entity entity = type.create(level, reason);
        if (entity == null) {
            return null;
        }
        if (!expectedClass.isInstance(entity)) {
            throw new IllegalStateException("Vanilla fixture entity type " + path + " created "
                    + entity.getClass().getName() + ", not " + expectedClass.getName());
        }
        return expectedClass.cast(entity);
    }
}
