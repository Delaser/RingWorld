package dev.ringworld.net;

import dev.ringworld.world.RingWorldSettings;
import dev.ringworld.world.RingTerrainNoiseMapping;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingSettingsHandshakeTest {
    private static Stream<Arguments> layouts() {
        return Stream.of(
                Arguments.of("safe-small", 2_048, 416, 160),
                Arguments.of("production", 16_384, 256, 160),
                Arguments.of("former-wide", 15_552, 4_096, 160),
                Arguments.of("custom-wall", 4_096, 640, 192));
    }

    @ParameterizedTest(name = "{0}: immutable settings survive payload and acknowledgement")
    @MethodSource("layouts")
    void immutableLayoutAcknowledgementRoundTrips(
            String name, int circumference, int width, int wallHeight) {
        RingWorldSettings settings = new RingWorldSettings(width, circumference,
                0x5EEDL, wallHeight, RingWorldSettings.FORMAT_VERSION);
        RingSettingsPayload payload = RingSettingsHandshake.payloadFor(settings);

        assertTrue(RingSettingsHandshake.hasMatchingPayloadFingerprint(payload));
        assertTrue(RingSettingsHandshake.accepts(settings,
                RingSettingsHandshake.acknowledgementFor(payload)));
    }

    @ParameterizedTest(name = "{0}: mismatched immutable layout is rejected")
    @MethodSource("layouts")
    void changedFingerprintOrFormatIsRejected(
            String name, int circumference, int width, int wallHeight) {
        RingWorldSettings settings = new RingWorldSettings(width, circumference,
                0x5EEDL, wallHeight, RingWorldSettings.FORMAT_VERSION);
        RingSettingsPayload payload = RingSettingsHandshake.payloadFor(settings);
        RingSettingsPayload changedWall = new RingSettingsPayload(payload.width(),
                payload.circumference(), payload.seed(), payload.wallHeight() + 16,
                payload.surfaceReferenceY(), payload.terrainNoiseMapping(),
                payload.formatVersion(), payload.fingerprint());
        RingSettingsPayload changedMapping = new RingSettingsPayload(payload.width(),
                payload.circumference(), payload.seed(), payload.wallHeight(),
                payload.surfaceReferenceY(), RingTerrainNoiseMapping.LEGACY_AXIAL,
                payload.formatVersion(), payload.fingerprint());
        RingSettingsPayload unknownMapping = new RingSettingsPayload(payload.width(),
                payload.circumference(), payload.seed(), payload.wallHeight(),
                payload.surfaceReferenceY(), 99,
                payload.formatVersion(), payload.fingerprint());

        assertFalse(RingSettingsHandshake.hasMatchingPayloadFingerprint(changedWall));
        assertFalse(RingSettingsHandshake.hasMatchingPayloadFingerprint(changedMapping));
        assertFalse(RingSettingsHandshake.hasMatchingPayloadFingerprint(unknownMapping));
        assertFalse(RingSettingsHandshake.accepts(settings,
                new RingSettingsAckPayload(payload.formatVersion(), payload.fingerprint() ^ 1L)));
        assertFalse(RingSettingsHandshake.accepts(settings,
                new RingSettingsAckPayload(payload.formatVersion() + 1, payload.fingerprint())));
    }
}
