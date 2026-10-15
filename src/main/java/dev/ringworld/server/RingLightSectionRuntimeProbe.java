package dev.ringworld.server;

import dev.ringworld.RingWorldMod;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.chunk.DataLayer;
import net.minecraft.world.level.lighting.DataLayerStorageMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Opt-in assertions on real transformed wrappers; only detached copies are mutated. */
public final class RingLightSectionRuntimeProbe {
    private RingLightSectionRuntimeProbe() { }

    public static void verify(DataLayerStorageMap<?> initial, LightLayer layer, boolean serverOverworld) {
        if (!Boolean.getBoolean("ringworld.lightStorageProbe")) return;
        require((((RingLightSectionStorage) initial).ringworld$table() != null) == serverOverworld,
                "light storage scope");
        DataLayerStorageMap<?> live = initial.copy();
        long key = 0x123456789ABCDEL;
        DataLayer original = new DataLayer();
        original.set(1, 2, 3, 11);
        live.setLayer(key, original);
        DataLayerStorageMap<?> published = live.copy();
        require((((RingLightSectionStorage) published).ringworld$table() != null) == serverOverworld,
                "snapshot storage scope");
        require(published.getLayer(key).get(1, 2, 3) == 11, "initial value");
        require(live.getLayer(key) == original, "vanilla value identity"); // Warm the native cache.
        DataLayer writable = live.copyDataLayer(key);
        require(writable != original && live.getLayer(key) == writable, "copyDataLayer cache invalidation");
        writable.set(1, 2, 3, 7);
        require(published.getLayer(key).get(1, 2, 3) == 11, "published value isolation");
        require(live.getLayer(key).get(1, 2, 3) == 7, "writer value");
        live.removeLayer(key);
        live.clearCache(); // Vanilla's caller-owned invalidation protocol.
        require(!live.hasLayer(key) && live.getLayer(key) == null, "removal after cache clear");
        require(published.hasLayer(key), "published key ownership");

        published.disableCache();
        CountDownLatch reading = new CountDownLatch(1);
        CompletableFuture<Void> reader = CompletableFuture.runAsync(() -> {
            reading.countDown();
            for (int i = 0; i < 100_000; i++) {
                require(published.getLayer(key).get(1, 2, 3) == 11, "concurrent published read");
            }
        });
        try {
            require(reading.await(5, TimeUnit.SECONDS), "reader start deadline");
            for (int i = 0; i < 1_000; i++) {
                DataLayerStorageMap<?> branch = published.copy();
                branch.copyDataLayer(key).set(1, 2, 3, i & 15);
                branch.removeLayer(key);
            }
            reader.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("light storage probe interrupted", failure);
        } catch (Exception failure) {
            throw new IllegalStateException("light storage probe failed", failure);
        }
        RingWorldMod.LOGGER.info("[light-storage-probe] PASS layer={} overworldTable={} cache=true values=true publication=true",
                layer, serverOverworld);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("light storage probe: " + message);
    }
}
