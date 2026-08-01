package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AtlasPregenerationHeadlessPolicyTest {
    @Test
    void headlessRequestSuppressesBackgroundAndMayOnlyReplaceUnstartedIdleHandle() {
        AtlasPregenerationOptions headless = AtlasPregenerationOptions.headlessPrewarmDefaults();
        assertTrue(AtlasPregenerationHeadlessPolicy.suppressesBackgroundAutostart(true));
        assertFalse(AtlasPregenerationHeadlessPolicy.suppressesBackgroundAutostart(false));
        assertTrue(AtlasPregenerationHeadlessPolicy.mayReplaceIdleHandle(AtlasPregenerationState.IDLE, headless));
        assertFalse(AtlasPregenerationHeadlessPolicy.mayReplaceIdleHandle(AtlasPregenerationState.RUNNING, headless));
        assertFalse(AtlasPregenerationHeadlessPolicy.mayReplaceIdleHandle(AtlasPregenerationState.IDLE,
                AtlasPregenerationOptions.backgroundDefaults()));
    }
}
