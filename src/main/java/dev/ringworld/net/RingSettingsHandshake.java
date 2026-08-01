package dev.ringworld.net;

import dev.ringworld.world.RingLayoutFingerprint;
import dev.ringworld.world.RingWorldSettings;

/** Loader-neutral immutable-layout handshake derivation and comparison. */
public final class RingSettingsHandshake {
    private RingSettingsHandshake() { }

    public static RingSettingsPayload payloadFor(RingWorldSettings settings) {
        return new RingSettingsPayload(
                settings.widthBlocks(), settings.circumferenceBlocks(), settings.generatorSeed(),
                settings.wallHeightBlocks(), settings.surfaceReferenceY(), settings.formatVersion(),
                settings.layoutFingerprint());
    }

    /** Recomputes the identity from every settings field carried on the wire. */
    public static long fingerprintFor(RingSettingsPayload payload) {
        return RingLayoutFingerprint.compute(
                payload.width(), payload.circumference(), payload.seed(), payload.wallHeight(),
                payload.surfaceReferenceY(), payload.formatVersion());
    }

    public static boolean hasMatchingPayloadFingerprint(RingSettingsPayload payload) {
        return fingerprintFor(payload) == payload.fingerprint();
    }

    public static RingSettingsAckPayload acknowledgementFor(RingSettingsPayload payload) {
        return new RingSettingsAckPayload(payload.formatVersion(), fingerprintFor(payload));
    }

    public static boolean accepts(RingWorldSettings settings, RingSettingsAckPayload acknowledgement) {
        return acknowledgement.formatVersion() == settings.formatVersion()
                && acknowledgement.fingerprint() == settings.layoutFingerprint();
    }
}
