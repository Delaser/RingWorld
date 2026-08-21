package dev.ringworld.net;

import dev.ringworld.RingWorldMod;
import dev.ringworld.world.AtlasPregenerationProgress;
import dev.ringworld.world.AtlasPregenerationState;
import dev.ringworld.world.AtlasPregenerationStatus;
import java.time.Duration;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Complete immutable status/progress snapshot for atlas generation, wire layout v1. */
public record RingAtlasPregenerationStatusPayload(AtlasPregenerationStatus status)
        implements CustomPacketPayload {
    private static final int MAX_MESSAGE = 1_024;
    public static final Type<RingAtlasPregenerationStatusPayload> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(RingWorldMod.MOD_ID, "atlas_pregen_status_v1"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RingAtlasPregenerationStatusPayload> CODEC =
            StreamCodec.of(RingAtlasPregenerationStatusPayload::encode, RingAtlasPregenerationStatusPayload::decode);

    public RingAtlasPregenerationStatusPayload { if (status == null) throw new IllegalArgumentException("status is required"); }

    private static void encode(RegistryFriendlyByteBuf buf, RingAtlasPregenerationStatusPayload payload) {
        AtlasPregenerationStatus status = payload.status;
        AtlasPregenerationProgress progress = status.progress();
        buf.writeLong(status.worldHash());
        buf.writeVarInt(status.circumferenceBlocks());
        buf.writeVarInt(status.widthBlocks());
        buf.writeVarInt(status.atlasFormat());
        buf.writeVarInt(status.sampleStep());
        buf.writeVarLong(status.canonicalChunks());
        buf.writeVarLong(status.completedCanonicalChunks());
        buf.writeVarInt(progress.state().wireValue());
        buf.writeVarLong(progress.completedChunks());
        buf.writeVarLong(progress.totalChunks());
        buf.writeVarInt(progress.presentCells());
        buf.writeVarInt(progress.totalCells());
        buf.writeDouble(progress.cellsPerSecond());
        buf.writeVarLong(progress.elapsed().toMillis());
        writeOptionalDuration(buf, progress.eta());
        writeOptionalString(buf, progress.lastError());
        buf.writeBoolean(status.canControl());
        writeOptionalString(buf, status.message());
    }

    private static RingAtlasPregenerationStatusPayload decode(RegistryFriendlyByteBuf buf) {
        long worldHash = buf.readLong();
        int circumference = buf.readVarInt();
        int width = buf.readVarInt();
        int atlasFormat = buf.readVarInt();
        int sampleStep = buf.readVarInt();
        long canonicalChunks = buf.readVarLong();
        long completedCanonicalChunks = buf.readVarLong();
        AtlasPregenerationState state = AtlasPregenerationState.fromWireValue(buf.readVarInt());
        long completedChunks = buf.readVarLong();
        long totalChunks = buf.readVarLong();
        int presentCells = buf.readVarInt();
        int totalCells = buf.readVarInt();
        double rate = buf.readDouble();
        Duration elapsed = Duration.ofMillis(buf.readVarLong());
        Optional<Duration> eta = readOptionalDuration(buf);
        Optional<String> error = readOptionalString(buf);
        boolean canControl = buf.readBoolean();
        Optional<String> message = readOptionalString(buf);
        return new RingAtlasPregenerationStatusPayload(new AtlasPregenerationStatus(worldHash, circumference,
                width, atlasFormat, sampleStep, canonicalChunks, completedCanonicalChunks,
                new AtlasPregenerationProgress(state, completedChunks, totalChunks, presentCells, totalCells,
                        rate, elapsed, eta, error), canControl, message));
    }

    private static void writeOptionalDuration(RegistryFriendlyByteBuf buf, Optional<Duration> value) {
        buf.writeBoolean(value.isPresent());
        value.ifPresent(duration -> buf.writeVarLong(duration.toMillis()));
    }
    private static Optional<Duration> readOptionalDuration(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? Optional.of(Duration.ofMillis(buf.readVarLong())) : Optional.empty();
    }
    private static void writeOptionalString(RegistryFriendlyByteBuf buf, Optional<String> value) {
        buf.writeBoolean(value.isPresent());
        value.ifPresent(text -> buf.writeUtf(text, MAX_MESSAGE));
    }
    private static Optional<String> readOptionalString(RegistryFriendlyByteBuf buf) {
        return buf.readBoolean() ? Optional.of(buf.readUtf(MAX_MESSAGE)) : Optional.empty();
    }

    @Override public Type<? extends CustomPacketPayload> type() { return ID; }
}
