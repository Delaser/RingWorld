package dev.ringworld.client;

import dev.ringworld.net.RingAtlasPregenerationControlPayload;
import dev.ringworld.net.RingAtlasPregenerationStatusPayload;
import dev.ringworld.net.RingAtlasPregenerationStatusRequestPayload;
import dev.ringworld.world.AtlasPregenerationAction;
import dev.ringworld.world.AtlasPregenerationState;
import dev.ringworld.world.AtlasPregenerationStatus;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** Client session cache for authoritative atlas-generation snapshots. */
public final class AtlasPregenerationClientState {
    private static AtlasPregenerationStatus status;

    private AtlasPregenerationClientState() { }

    public static Optional<AtlasPregenerationStatus> status() { return Optional.ofNullable(status); }

    public static boolean isForCurrentRing() {
        return status != null && ClientRingState.geometry() != null
                && status.circumferenceBlocks() == ClientRingState.geometry().circumferenceBlocks()
                && status.widthBlocks() == ClientRingState.geometry().widthBlocks();
    }

    public static void install(Minecraft client, RingAtlasPregenerationStatusPayload payload) {
        AtlasPregenerationStatus next = payload.status();
        if (ClientRingState.geometry() == null
                || next.circumferenceBlocks() != ClientRingState.geometry().circumferenceBlocks()
                || next.widthBlocks() != ClientRingState.geometry().widthBlocks()) return;
        AtlasPregenerationStatus previous = status;
        status = next;
        // A first snapshot may legitimately be already complete (reconnect or
        // reopening the map). Only a live transition for this atlas gets a toast.
        if (previous != null && previous.worldHash() == next.worldHash()
                && previous.progress().state() != AtlasPregenerationState.COMPLETE
                && next.progress().state() == AtlasPregenerationState.COMPLETE) {
            SystemToast.add(client.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
                    Component.literal("RingWorld Map"), Component.literal("Entire Ring generation is complete."));
        }
    }

    public static void request(long worldHash) {
        if (RingClientPayloadTransport.canSend(RingAtlasPregenerationStatusRequestPayload.ID)) {
            RingClientPayloadTransport.send(new RingAtlasPregenerationStatusRequestPayload(worldHash));
        }
    }

    /** Shared client entrypoint used by the loader-neutral map screen. */
    public static boolean requestCurrent() {
        long hash = status == null ? ClientRingState.serverAtlasWorldHash() : status.worldHash();
        if ((status == null && !ClientRingState.hasServerAtlasWorldHash())
                || !RingClientPayloadTransport.canSend(RingAtlasPregenerationStatusRequestPayload.ID)) return false;
        request(hash);
        return true;
    }

    public static boolean canRequestCurrent() {
        return (status != null || ClientRingState.hasServerAtlasWorldHash())
                && RingClientPayloadTransport.canSend(RingAtlasPregenerationStatusRequestPayload.ID);
    }

    public static void control(long worldHash, AtlasPregenerationAction action) {
        if (RingClientPayloadTransport.canSend(RingAtlasPregenerationControlPayload.ID)) {
            RingClientPayloadTransport.send(new RingAtlasPregenerationControlPayload(worldHash, action));
        }
    }

    public static void clear() { status = null; }

    public static boolean sessionCleared() { return status == null; }
}
