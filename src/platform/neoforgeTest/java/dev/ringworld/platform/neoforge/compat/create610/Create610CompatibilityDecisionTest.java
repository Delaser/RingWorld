package dev.ringworld.platform.neoforge.compat.create610;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Create610CompatibilityDecisionTest {
    @Test
    void absentCreateSkipsSilentlyRegardlessOfOtherVersions() {
        var result = Create610CompatibilityDecision.evaluate(
                "unexpected", "unexpected", null, null);

        assertEquals(Create610CompatibilityDecision.State.CREATE_ABSENT, result.state());
        assertFalse(result.enabled());
        assertNull(result.observedTuple());
    }

    @Test
    void exactQualifiedTupleEnablesTheAdapter() {
        var result = Create610CompatibilityDecision.evaluate(
                "1.21.1", "21.1.239", "6.0.10", "1.0.6");

        assertEquals(Create610CompatibilityDecision.State.EXACT, result.state());
        assertTrue(result.enabled());
    }

    @Test
    void everyVersionBoundaryFailsClosed() {
        assertUnqualified("1.21.2", "21.1.239", "6.0.10", "1.0.6");
        assertUnqualified("1.21.1", "21.1.238", "6.0.10", "1.0.6");
        assertUnqualified("1.21.1", "21.1.239", "6.0.11", "1.0.6");
        assertUnqualified("1.21.1", "21.1.239", "6.0.10", "1.0.7");
        assertUnqualified("1.21.1", "21.1.239", "6.0.10", null);
    }

    private static void assertUnqualified(String minecraft, String neoForge,
                                          String create, String flywheel) {
        var result = Create610CompatibilityDecision.evaluate(
                minecraft, neoForge, create, flywheel);
        assertEquals(Create610CompatibilityDecision.State.UNQUALIFIED, result.state());
        assertFalse(result.enabled());
        assertTrue(result.observedTuple().contains("Minecraft=" + minecraft));
    }
}
