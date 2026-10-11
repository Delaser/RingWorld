package dev.ringworld.mixin;

import dev.ringworld.server.RingLightSectionStorage;
import dev.ringworld.server.RingLightSectionTable;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Preserve vanilla's caches and DataLayer ownership while partitioning the lookup copies. */
@Mixin(DataLayerStorageMap.class)
abstract class DataLayerStorageMapMixin implements RingLightSectionStorage {
    @Shadow @Final protected Long2ObjectOpenHashMap<DataLayer> map;
    @Unique private RingLightSectionTable<DataLayer> ringworld$table;
    @Override public RingLightSectionTable<DataLayer> ringworld$table() { return ringworld$table; }
    @Override public void ringworld$table(RingLightSectionTable<DataLayer> table) {
        if (ringworld$table == null && table != null && !map.isEmpty()) {
            // Normally empty at construction; preserve any layers supplied by another initializer.
            map.long2ObjectEntrySet().forEach(entry -> table.put(entry.getLongKey(), entry.getValue()));
            map.clear();
        }
        ringworld$table = table;
    }

    @Redirect(method = {"getLayer", "copyDataLayer"}, at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;get(J)Ljava/lang/Object;"))
    private Object ringworld$get(Long2ObjectOpenHashMap<DataLayer> map, long key) {
        return ringworld$table == null ? map.get(key) : ringworld$table.get(key);
    }

    @Redirect(method = {"setLayer", "copyDataLayer"}, at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;put(JLjava/lang/Object;)Ljava/lang/Object;"))
    private Object ringworld$put(Long2ObjectOpenHashMap<DataLayer> map, long key, Object value) {
        return ringworld$table == null ? map.put(key, (DataLayer) value) : ringworld$table.put(key, (DataLayer) value);
    }

    @Redirect(method = "hasLayer", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;containsKey(J)Z"))
    private boolean ringworld$contains(Long2ObjectOpenHashMap<DataLayer> map, long key) {
        return ringworld$table == null ? map.containsKey(key) : ringworld$table.containsKey(key);
    }

    @Redirect(method = "removeLayer", at = @At(value = "INVOKE", target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;remove(J)Ljava/lang/Object;"))
    private Object ringworld$remove(Long2ObjectOpenHashMap<DataLayer> map, long key) {
        return ringworld$table == null ? map.remove(key) : ringworld$table.remove(key);
    }
}
