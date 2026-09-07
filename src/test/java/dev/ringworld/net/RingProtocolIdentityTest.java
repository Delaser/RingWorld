package dev.ringworld.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingProtocolIdentityTest {
    @Test
    void settingsChannelsNameTheirCurrentWireLayout() {
        assertEquals("ringworld:settings_v7", RingSettingsPayload.ID.id().toString());
        assertEquals("ringworld:settings_ack_v3", RingSettingsAckPayload.ID.id().toString());
        assertEquals("ringworld:sky_profile_v1", RingSkyProfilePayload.ID.id().toString());
        assertEquals("ringworld:terrain_preview_v2",
                RingTerrainPreviewPayload.ID.id().toString());
    }

    @Test
    void allThreeClientboundChannelsAreRequiredBeforeHandshake() {
        var required = RingProtocolCapabilities.requiredClientbound();
        assertEquals(java.util.List.of(
                RingSettingsPayload.ID, RingSkyProfilePayload.ID, RingTerrainPreviewPayload.ID),
                required);
        assertTrue(RingProtocolCapabilities.supportsRequiredClientbound(required::contains));
        for (var missing : required) {
            assertFalse(RingProtocolCapabilities.supportsRequiredClientbound(
                    type -> type != missing));
        }
    }

    @Test
    void atlasPregenerationChannelsNameTheirIndependentWireLayout() {
        assertEquals("ringworld:atlas_pregen_control_v1", RingAtlasPregenerationControlPayload.ID.id().toString());
        assertEquals("ringworld:atlas_pregen_status_request_v1", RingAtlasPregenerationStatusRequestPayload.ID.id().toString());
        assertEquals("ringworld:atlas_pregen_status_v1", RingAtlasPregenerationStatusPayload.ID.id().toString());
    }

    @Test
    void terrainAtlasChannelsNameTheirRevisionedWireLayout() {
        assertEquals("ringworld:terrain_atlas_metadata_v3", RingTerrainAtlasMetadataPayload.ID.id().toString());
        assertEquals("ringworld:terrain_atlas_request_v2", RingTerrainAtlasRequestPayload.ID.id().toString());
        assertEquals("ringworld:terrain_atlas_tile_v3", RingTerrainAtlasTilePayload.ID.id().toString());
        assertEquals("ringworld:terrain_atlas_revision_v1", RingTerrainAtlasRevisionPayload.ID.id().toString());
    }
}
