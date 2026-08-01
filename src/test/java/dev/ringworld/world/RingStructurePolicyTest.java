package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RingStructurePolicyTest {
    @Test
    void strongholdGuaranteeIsAnExplicitSavedPolicyBit() {
        assertTrue(new RingStructurePolicy(
                RingStructurePolicy.GUARANTEE_STRONGHOLD,
                RingStructurePolicy.FORMAT_VERSION).guaranteesStronghold());
        assertFalse(new RingStructurePolicy(0,
                RingStructurePolicy.FORMAT_VERSION).guaranteesStronghold());
    }

    @Test
    void monumentRequestAndTerminalResolutionAreExplicitSavedPolicyState() {
        RingStructurePolicy requested = new RingStructurePolicy(
                RingStructurePolicy.GUARANTEE_STRONGHOLD | RingStructurePolicy.REQUEST_OCEAN_MONUMENT,
                RingStructurePolicy.FORMAT_VERSION, RingMonumentResolution.pending());
        RingMonumentResolution terminal = RingMonumentResolution.satisfied(
                new RingMonumentPlacement.Candidate(15, -1));
        RingStructurePolicy resolved = new RingStructurePolicy(requested.guarantees(),
                RingStructurePolicy.FORMAT_VERSION, terminal);

        assertTrue(requested.requestsOceanMonument());
        assertEquals(RingMonumentResolution.Status.PENDING, requested.oceanMonument().status());
        assertEquals(RingMonumentResolution.Status.SATISFIED, resolved.oceanMonument().status());
        assertEquals(15, resolved.oceanMonument().candidate().chunkX());
    }

    @Test
    void legacyVersionOnePolicyCannotGainAMonumentRequest() {
        RingStructurePolicy legacy = new RingStructurePolicy(
                RingStructurePolicy.GUARANTEE_STRONGHOLD, 1);

        assertFalse(legacy.requestsOceanMonument());
        assertEquals(RingMonumentResolution.Status.DISABLED, legacy.oceanMonument().status());
    }
}
