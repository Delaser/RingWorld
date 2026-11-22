package dev.ringworld;

import dev.ringworld.api.RingCompatibilityContract;
import java.util.Collection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loader-neutral identity and logging shared by every platform adapter. */
public final class RingWorldMod {
    public static final String MOD_ID = "ringworld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void logLoadedConflicts(Collection<String> loadedModIds) {
        for (var conflict : RingCompatibilityContract.findLoadedConflicts(loadedModIds)) {
            if (conflict.area() == RingCompatibilityContract.Area.SHADERS) {
                LOGGER.warn("RingWorld shader compatibility with {} ({}): {}.",
                        conflict.displayName(), conflict.modId(), conflict.reason());
            } else {
                LOGGER.error("Unsupported RingWorld combination detected: {} ({}): {}. "
                                + "Remove it before reporting RingWorld rendering or topology defects.",
                        conflict.displayName(), conflict.modId(), conflict.reason());
            }
        }
    }

    private RingWorldMod() { }
}
