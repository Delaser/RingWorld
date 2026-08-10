package dev.ringworld.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingProtocolIdentityTest {
    @Test
    void settingsChannelsNameTheirCurrentWireLayout() {
        assertEquals("ringworld:settings_v3", RingSettingsPayload.ID.id().toString());
        assertEquals("ringworld:settings_ack_v3", RingSettingsAckPayload.ID.id().toString());
    }

    @Test
    void atlasPregenerationChannelsNameTheirIndependentWireLayout() {
        assertEquals("ringworld:atlas_pregen_control_v1", RingAtlasPregenerationControlPayload.ID.id().toString());
        assertEquals("ringworld:atlas_pregen_status_request_v1", RingAtlasPregenerationStatusRequestPayload.ID.id().toString());
        assertEquals("ringworld:atlas_pregen_status_v1", RingAtlasPregenerationStatusPayload.ID.id().toString());
    }

    @Test
    void terrainAtlasChannelsNameTheirRevisionedWireLayout() {
        assertEquals("ringworld:terrain_atlas_metadata_v2", RingTerrainAtlasMetadataPayload.ID.id().toString());
        assertEquals("ringworld:terrain_atlas_request_v2", RingTerrainAtlasRequestPayload.ID.id().toString());
        assertEquals("ringworld:terrain_atlas_tile_v2", RingTerrainAtlasTilePayload.ID.id().toString());
        assertEquals("ringworld:terrain_atlas_revision_v1", RingTerrainAtlasRevisionPayload.ID.id().toString());
    }
}
