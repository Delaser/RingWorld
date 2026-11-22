package dev.ringworld.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import net.minecraft.client.Minecraft;

/** Client-thread preference for the active pack. Zero preserves the ordinary Atlas colors. */
public final class RingAtlasContrast {
    private static String cachedPack;
    private static int percent;

    private RingAtlasContrast() {}

    private static Path file(String pack) {
        // Pack names come from the renderer and may contain arbitrary path characters.
        UUID key = UUID.nameUUIDFromBytes(pack.getBytes(StandardCharsets.UTF_8));
        return Minecraft.getInstance().gameDirectory.toPath()
                .resolve("config/ringworld-atlas-contrast").resolve(key + ".txt");
    }

    private static int current(String pack) {
        if (!pack.equals(cachedPack)) {
            cachedPack = pack;
            percent = 0;
            try {
                percent = Math.clamp(Integer.parseInt(Files.readString(file(pack)).trim()), 0, 100);
            } catch (NoSuchFileException ignored) {
                // First use of this pack.
            } catch (IOException | NumberFormatException e) {
                com.mojang.logging.LogUtils.getLogger().warn("Could not read Atlas contrast for {}", pack, e);
            }
        }
        return percent;
    }

    /** Called only when drawing into an active pack's lit scene. */
    public static float strength() {
        String pack = RingShaderSupport.activePackName();
        return pack == null ? 0 : current(pack) / 100.0F;
    }

    public static String command(Integer value) {
        String pack = RingShaderSupport.activePackName();
        if (pack == null) return "Enable a shader pack to set its Atlas contrast.";
        if (value != null) {
            Path path = file(pack);
            try {
                if (value == 0) {
                    Files.deleteIfExists(path);
                } else {
                    Files.createDirectories(path.getParent());
                    Path temp = Files.createTempFile(path.getParent(), "contrast-", ".tmp");
                    try {
                        Files.writeString(temp, Integer.toString(value));
                        try {
                            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                        } catch (AtomicMoveNotSupportedException e) {
                            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } finally {
                        Files.deleteIfExists(temp);
                    }
                }
            } catch (IOException e) {
                return "Could not save Atlas contrast for " + pack + ": " + e.getMessage();
            }
            cachedPack = pack;
            percent = value;
        }
        return "Atlas contrast for " + pack + ": " + current(pack) + "%"
                + (RingShaderSupport.enabled() ? "." : " (shader support is off).");
    }
}
