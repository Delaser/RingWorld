package dev.ringworld.client;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Loader-neutral outbound payload capability and delivery adapter for client code. */
public final class RingClientPayloadTransport {
    private static Adapter adapter = new Adapter() {
        @Override
        public boolean canSend(CustomPacketPayload.Type<?> type) {
            return false;
        }

        @Override
        public void send(CustomPacketPayload payload) {
            throw new IllegalStateException("RingWorld client payload transport is not configured");
        }
    };

    private RingClientPayloadTransport() { }

    public static void configure(Adapter configuredAdapter) {
        if (configuredAdapter == null) throw new IllegalArgumentException("payload transport is required");
        adapter = configuredAdapter;
    }

    public static boolean canSend(CustomPacketPayload.Type<?> type) {
        return adapter.canSend(type);
    }

    public static void send(CustomPacketPayload payload) {
        adapter.send(payload);
    }

    /** Narrow loader-owned outbound payload capability and delivery adapter. */
    public interface Adapter {
        boolean canSend(CustomPacketPayload.Type<?> type);
        void send(CustomPacketPayload payload);
    }
}
