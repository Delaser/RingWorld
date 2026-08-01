package dev.ringworld.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingProtocolIdentityTest {
    @Test
    void settingsChannelsNameTheirCurrentWireLayout() {
        assertEquals("ringworld:settings_v2", RingSettingsPayload.ID.id().toString());
        assertEquals("ringworld:settings_ack_v2", RingSettingsAckPayload.ID.id().toString());
    }

    @Test
    void atlasPregenerationChannelsNameTheirIndependentWireLayout() {
        assertEquals("ringworld:atlas_pregen_control_v1", RingAtlasPregenerationControlPayload.ID.id().toString());
        assertEquals("ringworld:atlas_pregen_status_request_v1", RingAtlasPregenerationStatusRequestPayload.ID.id().toString());
        assertEquals("ringworld:atlas_pregen_status_v1", RingAtlasPregenerationStatusPayload.ID.id().toString());
    }
}
