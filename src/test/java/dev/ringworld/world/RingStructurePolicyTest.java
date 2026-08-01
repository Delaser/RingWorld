package dev.ringworld.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingStructurePolicyTest {
    @Test
    void strongholdGuaranteeIsAnExplicitSavedPolicyBit() {
        assertTrue(new RingStructurePolicy(
                RingStructurePolicy.GUARANTEE_STRONGHOLD,
                RingStructurePolicy.FORMAT_VERSION).guaranteesStronghold());
        assertFalse(new RingStructurePolicy(0,
                RingStructurePolicy.FORMAT_VERSION).guaranteesStronghold());
    }
}
