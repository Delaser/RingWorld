package dev.ringworld.net;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Loader-neutral required-channel contract used before the settings handshake begins. */
public final class RingProtocolCapabilities {
    private static final List<CustomPacketPayload.Type<? extends CustomPacketPayload>>
            REQUIRED_CLIENTBOUND = List.of(
                    RingSettingsPayload.ID, RingSkyProfilePayload.ID, RingTerrainPreviewPayload.ID);

    private RingProtocolCapabilities() { }

    public static List<CustomPacketPayload.Type<? extends CustomPacketPayload>>
            requiredClientbound() {
        return REQUIRED_CLIENTBOUND;
    }

    public static boolean supportsRequiredClientbound(
            Predicate<CustomPacketPayload.Type<? extends CustomPacketPayload>> supported) {
        if (supported == null) throw new IllegalArgumentException("capability query is required");
        return REQUIRED_CLIENTBOUND.stream().allMatch(supported);
    }
}
