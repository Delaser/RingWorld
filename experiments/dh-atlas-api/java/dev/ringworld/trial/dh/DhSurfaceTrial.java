package dev.ringworld.trial.dh;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;

import dev.ringworld.RingWorldMod;
import dev.ringworld.client.ClientRingState;
import dev.ringworld.client.render.RingSurfaceTextureRenderer;
import dev.ringworld.world.RingTerrainAtlas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

public final class DhSurfaceTrial extends DhApiBeforeRenderEvent {
    private static ClientLevel owner;
    public static int cancelled;
    private static RingTerrainAtlas placeholder;
    private static DhSurfaceReader reader;
    private static long nextLookup;
    private static int revision;
    private static Object preview;
    private static RingTerrainAtlas selected;

    public static void register() {
        DhApi.events.bind(DhApiBeforeRenderEvent.class, new DhSurfaceTrial());
    }

    public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
        var client = Minecraft.getInstance();
        if (client.level != null
                && ClientRingState.geometry(client.level) != null
                && event.value.clientLevelWrapper.getWrappedMcObject() == client.level) {
            event.cancelEvent();
            cancelled++;
        }
    }

    public static RingTerrainAtlas select(RingTerrainAtlas serverAtlas) {
        var client = Minecraft.getInstance();
        if (client.level == null
                || serverAtlas == null
                || ClientRingState.geometry(client.level) == null) {
            return serverAtlas;
        }
        if (owner != client.level || placeholder.worldHash() != serverAtlas.worldHash()) {
            clear();
            owner = client.level;
            placeholder =
                    new RingTerrainAtlas(
                            serverAtlas.geometry(),
                            serverAtlas.worldHash(),
                            serverAtlas.sampleStep());
        }
        long now = System.nanoTime();
        if (reader == null
                && selected == placeholder
                && RingSurfaceTextureRenderer.displayReady()
                && now >= nextLookup) {
            nextLookup = now + 500_000_000L;
            var proxy = DhApi.Delayed.worldProxy;
            if (proxy != null && proxy.worldLoaded()) {
                IDhApiLevelWrapper level = proxy.getSinglePlayerLevel();
                if (level != null && level.getWrappedMcObject() == owner && client.player != null) {
                    reader = new DhSurfaceReader(level, placeholder);
                    reader.start();
                    announceOnFirstJoin(client, serverAtlas.worldHash());
                }
            }
        }
        var ready = reader == null ? null : reader.ready;
        var current = ready == null ? placeholder : ready;
        if (selected != current || (ready == null && ClientRingState.terrainPreview() != preview)) {
            selected = current;
            preview = ClientRingState.terrainPreview();
            revision++;
        }
        return current;
    }

    private static void announceOnFirstJoin(Minecraft client, long worldHash) {
        var server = client.getSingleplayerServer();
        Path notices;
        if (server != null) {
            notices = server.getWorldPath(LevelResource.ROOT).resolve("data/ringworld/dh-notices");
        } else {
            var entry = client.getCurrentServer();
            String address =
                    entry != null
                            ? entry.ip
                            : client.getConnection().getConnection().getRemoteAddress().toString();
            UUID world =
                    UUID.nameUUIDFromBytes(
                            (address + "/" + worldHash).getBytes(StandardCharsets.UTF_8));
            notices =
                    client.gameDirectory
                            .toPath()
                            .resolve("ringworld/dh-notices")
                            .resolve(world.toString());
        }
        try {
            Files.createDirectories(notices);
            Files.createFile(notices.resolve(client.getUser().getProfileId() + ".seen"));
        } catch (FileAlreadyExistsException seen) {
            return;
        } catch (IOException failure) {
            RingWorldMod.LOGGER.warn(
                    "Could not remember the Distant Horizons join notice", failure);
        }
        client.player.sendSystemMessage(
                Component.literal(
                        "Distant Horizons is enabled. You can play while the distant ring"
                            + " generates. RingWorld will switch from the placeholder to detailed"
                            + " terrain once the whole ring is ready."));
    }

    public static RingTerrainAtlas displayedSource(RingTerrainAtlas fallback) {
        return selected == null ? fallback : selected;
    }

    public static int revision(int fallback) {
        return selected == null ? fallback : revision;
    }

    public static void clear() {
        if (reader != null) reader.interrupt();
        reader = null;
        nextLookup = 0;
        owner = null;
        placeholder = selected = null;
        preview = null;
    }
}
