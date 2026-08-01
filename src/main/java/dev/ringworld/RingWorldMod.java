package dev.ringworld;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Loader-neutral identity and logging shared by every platform adapter. */
public final class RingWorldMod {
    public static final String MOD_ID = "ringworld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private RingWorldMod() { }
}
