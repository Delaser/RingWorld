package dev.ringworld.net;

import dev.ringworld.world.RingGeometry;
import dev.ringworld.world.RingSkyProfile;
import dev.ringworld.world.RingTerrainNoiseMapping;
import dev.ringworld.world.RingTerrainPreview;
import dev.ringworld.world.RingTerrainPreviewStage;
import dev.ringworld.world.RingWallStyle;
import dev.ringworld.world.RingWorldSettings;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RingProtocolCodecTest {
    @Test
    void settingsV5RoundTripsInTheDocumentedFieldOrder() {
        RingWallStyle wall = RingWallStyle.custom(
                9, RingWallStyle.Palette.INDUSTRIAL, RingWallStyle.Pattern.HYBRID, 35);
        RingSkyProfile sky = new RingSkyProfile(
                RingSkyProfile.Backdrop.NIGHT, RingSkyProfile.LightSource.LARGE,
                RingSkyProfile.FORMAT_VERSION);
        RingWorldSettings settings = new RingWorldSettings(
                640, 4_096, 0x1020304050607080L, 192, (int) RingGeometry.SURFACE_Y,
                RingTerrainNoiseMapping.CURRENT, wall, RingWorldSettings.FORMAT_VERSION);
        RingSettingsPayload payload = RingSettingsHandshake.payloadFor(settings, sky);

        byte[] encoded = encode(RingSettingsPayload.CODEC, payload);
        RegistryFriendlyByteBuf fields = wrapped(encoded);
        try {
            assertEquals(payload.width(), fields.readVarInt());
            assertEquals(payload.circumference(), fields.readVarInt());
            assertEquals(payload.seed(), fields.readLong());
            assertEquals(payload.wallHeight(), fields.readVarInt());
            assertEquals(payload.surfaceReferenceY(), fields.readVarInt());
            assertEquals(payload.terrainNoiseMapping(), fields.readVarInt());
            assertEquals(wall.thicknessBlocks(), fields.readVarInt());
            assertEquals(wall.palette().id(), fields.readVarInt());
            assertEquals(wall.pattern().id(), fields.readVarInt());
            assertEquals(wall.decayPercent(), fields.readVarInt());
            assertEquals(wall.formatVersion(), fields.readVarInt());
            assertEquals(sky.backdrop().id(), fields.readVarInt());
            assertEquals(sky.lightSource().id(), fields.readVarInt());
            assertEquals(sky.formatVersion(), fields.readVarInt());
            assertEquals(payload.formatVersion(), fields.readVarInt());
            assertEquals(payload.fingerprint(), fields.readLong());
            assertEquals(0, fields.readableBytes());
        } finally {
            fields.release();
        }

        RingSettingsPayload decoded = decode(RingSettingsPayload.CODEC, encoded);
        assertEquals(payload, decoded);
        assertTruncatedRejected(RingSettingsPayload.CODEC, payload);
    }

    @Test
    void settingsV5RejectsInvalidNestedAndGeometryValues() {
        RingSkyProfile sky = RingSkyProfile.DEFAULT;
        assertThrows(IllegalArgumentException.class, () -> new RingSettingsPayload(
                64, 2_048, 1L, 160, (int) RingGeometry.SURFACE_Y,
                RingTerrainNoiseMapping.CURRENT, RingWallStyle.LEGACY, sky,
                RingWorldSettings.FORMAT_VERSION, 1L));

        RegistryFriendlyByteBuf unknownPalette = buffer();
        try {
            unknownPalette.writeVarInt(256);
            unknownPalette.writeVarInt(2_048);
            unknownPalette.writeLong(1L);
            unknownPalette.writeVarInt(160);
            unknownPalette.writeVarInt((int) RingGeometry.SURFACE_Y);
            unknownPalette.writeVarInt(RingTerrainNoiseMapping.CURRENT);
            unknownPalette.writeVarInt(5);
            unknownPalette.writeVarInt(99);
            assertThrows(IllegalArgumentException.class,
                    () -> RingSettingsPayload.CODEC.decode(unknownPalette));
        } finally {
            unknownPalette.release();
        }
    }

    @Test
    void skyProfileV1RoundTripsAndRejectsUnknownValues() {
        RingSkyProfile profile = new RingSkyProfile(
                RingSkyProfile.Backdrop.VOID, RingSkyProfile.LightSource.NONE,
                RingSkyProfile.FORMAT_VERSION);
        RingSkyProfilePayload payload = RingSkyProfilePayload.from(profile);
        byte[] encoded = encode(RingSkyProfilePayload.CODEC, payload);

        RegistryFriendlyByteBuf fields = wrapped(encoded);
        try {
            assertEquals(profile.backdrop().id(), fields.readVarInt());
            assertEquals(profile.lightSource().id(), fields.readVarInt());
            assertEquals(profile.formatVersion(), fields.readVarInt());
            assertEquals(0, fields.readableBytes());
        } finally {
            fields.release();
        }
        assertEquals(payload, decode(RingSkyProfilePayload.CODEC, encoded));
        assertEquals(profile, payload.profile());
        assertThrows(IllegalArgumentException.class,
                () -> new RingSkyProfilePayload(99, 0, RingSkyProfile.FORMAT_VERSION));
        assertThrows(IllegalArgumentException.class,
                () -> new RingSkyProfilePayload(0, 99, RingSkyProfile.FORMAT_VERSION));
        assertThrows(IllegalArgumentException.class,
                () -> new RingSkyProfilePayload(0, 0, 99));
        assertTruncatedRejected(RingSkyProfilePayload.CODEC, payload);
    }

    @Test
    void terrainPreviewV2ValidatesStageBodyHashAndCompressedLimit() throws Exception {
        long worldHash = 0x1122334455667788L;
        RingTerrainPreview preview = new RingTerrainPreview(
                worldHash, 4, 1,
                new int[] {0x102030, 0x405060, 0x708090, 0xA0B0C0},
                new short[] {48, 64, 72, 96});
        RingTerrainPreviewPayload payload = new RingTerrainPreviewPayload(
                worldHash, RingTerrainPreviewStage.HIGH.wireValue(), preview.encode());
        byte[] encoded = encode(RingTerrainPreviewPayload.CODEC, payload);

        RegistryFriendlyByteBuf fields = wrapped(encoded);
        try {
            assertEquals(worldHash, fields.readLong());
            assertEquals(RingTerrainPreviewStage.HIGH.wireValue(), fields.readVarInt());
            assertArrayEquals(payload.data(),
                    fields.readByteArray(RingTerrainPreview.MAX_COMPRESSED_BYTES));
            assertEquals(0, fields.readableBytes());
        } finally {
            fields.release();
        }

        RingTerrainPreviewPayload decoded = decode(RingTerrainPreviewPayload.CODEC, encoded);
        assertEquals(payload.worldHash(), decoded.worldHash());
        assertEquals(payload.stage(), decoded.stage());
        assertArrayEquals(payload.data(), decoded.data());
        assertThrows(IllegalArgumentException.class,
                () -> new RingTerrainPreviewPayload(worldHash, 99, payload.data()));
        assertThrows(IllegalArgumentException.class,
                () -> new RingTerrainPreviewPayload(worldHash ^ 1L, payload.stage(), payload.data()));
        assertThrows(IllegalArgumentException.class,
                () -> new RingTerrainPreviewPayload(worldHash, payload.stage(), new byte[] {1}));

        RegistryFriendlyByteBuf oversized = buffer();
        try {
            oversized.writeLong(worldHash);
            oversized.writeVarInt(RingTerrainPreviewStage.CURRENT.wireValue());
            oversized.writeVarInt(RingTerrainPreview.MAX_COMPRESSED_BYTES + 1);
            assertThrows(RuntimeException.class,
                    () -> RingTerrainPreviewPayload.CODEC.decode(oversized));
        } finally {
            oversized.release();
        }
        assertTruncatedRejected(RingTerrainPreviewPayload.CODEC, payload);
    }

    @Test
    void previewStageAndSettingsAckWireIdentitiesStayStable() {
        assertEquals(0, RingTerrainPreviewStage.CURRENT.wireValue());
        assertEquals(1, RingTerrainPreviewStage.HIGH.wireValue());
        assertEquals(2, RingTerrainPreviewStage.VERY_HIGH.wireValue());
        assertEquals(3, RingTerrainPreviewStage.ULTRA.wireValue());
        assertThrows(IllegalArgumentException.class,
                () -> RingTerrainPreviewStage.fromWireValue(4));

        RingSettingsAckPayload acknowledgement = new RingSettingsAckPayload(4, 0x0102030405060708L);
        byte[] encoded = encode(RingSettingsAckPayload.CODEC, acknowledgement);
        RegistryFriendlyByteBuf fields = wrapped(encoded);
        try {
            assertEquals(4, fields.readVarInt());
            assertEquals(0x0102030405060708L, fields.readLong());
            assertEquals(0, fields.readableBytes());
        } finally {
            fields.release();
        }
        assertEquals(acknowledgement, decode(RingSettingsAckPayload.CODEC, encoded));
        assertTruncatedRejected(RingSettingsAckPayload.CODEC, acknowledgement);
    }

    private static <T> byte[] encode(
            StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            codec.encode(buffer, value);
            byte[] encoded = new byte[buffer.readableBytes()];
            buffer.getBytes(buffer.readerIndex(), encoded);
            return encoded;
        } finally {
            buffer.release();
        }
    }

    private static <T> T decode(
            StreamCodec<RegistryFriendlyByteBuf, T> codec, byte[] encoded) {
        RegistryFriendlyByteBuf buffer = wrapped(encoded);
        try {
            T value = codec.decode(buffer);
            assertEquals(0, buffer.readableBytes());
            return value;
        } finally {
            buffer.release();
        }
    }

    private static <T> void assertTruncatedRejected(
            StreamCodec<RegistryFriendlyByteBuf, T> codec, T value) {
        byte[] encoded = encode(codec, value);
        byte[] truncated = Arrays.copyOf(encoded, encoded.length - 1);
        RegistryFriendlyByteBuf buffer = wrapped(truncated);
        try {
            assertThrows(RuntimeException.class, () -> codec.decode(buffer));
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
    }

    private static RegistryFriendlyByteBuf wrapped(byte[] bytes) {
        return new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(bytes), RegistryAccess.EMPTY);
    }
}
