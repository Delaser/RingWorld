package dev.ringworld.net;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RingProtocolIdentityTest {
    @Test
    void settingsChannelsNameTheirCurrentWireLayout() {
        assertEquals("ringworld:settings_v2", RingSettingsPayload.ID.id().toString());
        assertEquals("ringworld:settings_ack_v2", RingSettingsAckPayload.ID.id().toString());
    }
}
