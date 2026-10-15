package dev.ringworld.world;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RingBuildingSettingsTest {
    @Test void missingSavedOptionDefaultsToOn() {
        assertTrue(RingBuildingSettings.codecForTests().parse(JsonOps.INSTANCE, new JsonObject())
                .getOrThrow().outsideBuilding());
    }
    @Test void bothStatesSurviveStorageRoundTrip() {
        for (boolean enabled : new boolean[]{false, true}) {
            var encoded = RingBuildingSettings.codecForTests()
                    .encodeStart(JsonOps.INSTANCE, new RingBuildingSettings(enabled)).getOrThrow();
            assertEquals(enabled, RingBuildingSettings.codecForTests()
                    .parse(JsonOps.INSTANCE, encoded).getOrThrow().outsideBuilding());
        }
    }
    @Test void disabledPolicyKeepsBothEdgesInclusiveButRejectsExterior() {
        var ring = new RingGeometry(128, 2048);
        assertTrue(RingBuildingSettings.allows(false, ring, -64));
        assertTrue(RingBuildingSettings.allows(false, ring, 63));
        assertFalse(RingBuildingSettings.allows(false, ring, -65));
        assertFalse(RingBuildingSettings.allows(false, ring, 64));
        assertTrue(RingBuildingSettings.allows(true, ring, -65));
        assertTrue(RingBuildingSettings.allows(true, ring, 64));
    }
}
