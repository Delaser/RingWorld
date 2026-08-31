package dev.ringworld.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;

/** Server-owned visual presentation of the RingWorld sky and visible light source. */
public record RingSkyProfile(Backdrop backdrop, LightSource lightSource, int formatVersion) {
    public static final int FORMAT_VERSION = 1;
    public static final RingSkyProfile DEFAULT = new RingSkyProfile(
            Backdrop.ATMOSPHERE, LightSource.SMALL, FORMAT_VERSION);

    public static final Codec<RingSkyProfile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("backdrop").xmap(Backdrop::fromId, Backdrop::id)
                    .forGetter(RingSkyProfile::backdrop),
            Codec.INT.fieldOf("lightSource").xmap(LightSource::fromId, LightSource::id)
                    .forGetter(RingSkyProfile::lightSource),
            Codec.INT.fieldOf("format").forGetter(RingSkyProfile::formatVersion)
    ).apply(instance, RingSkyProfile::new));

    public RingSkyProfile {
        if (backdrop == null) throw new IllegalArgumentException("sky backdrop is required");
        if (lightSource == null) throw new IllegalArgumentException("sky light source is required");
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported sky profile format " + formatVersion);
        }
    }

    public enum Backdrop {
        ATMOSPHERE(0, "Atmosphere"),
        NIGHT(1, "Night"),
        VOID(2, "Void");

        private final int id;
        private final String label;

        Backdrop(int id, String label) { this.id = id; this.label = label; }
        public int id() { return id; }
        public String label() { return label; }
        public Backdrop next() {
            Backdrop[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
        public static Backdrop fromId(int id) {
            return Arrays.stream(values()).filter(value -> value.id == id).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown sky backdrop " + id));
        }
    }

    public enum LightSource {
        SMALL(0, "Small", 3.0F),
        LARGE(1, "Large", 15.0F),
        NONE(2, "None", 0.0F);

        private final int id;
        private final String label;
        private final float halfWidth;

        LightSource(int id, String label, float halfWidth) {
            this.id = id;
            this.label = label;
            this.halfWidth = halfWidth;
        }
        public int id() { return id; }
        public String label() { return label; }
        public float halfWidth() { return halfWidth; }
        public LightSource next() {
            LightSource[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
        public static LightSource fromId(int id) {
            return Arrays.stream(values()).filter(value -> value.id == id).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown sky light source " + id));
        }
    }

    public RingSkyProfile withBackdrop(Backdrop value) {
        return new RingSkyProfile(value, lightSource, FORMAT_VERSION);
    }

    public RingSkyProfile withLightSource(LightSource value) {
        return new RingSkyProfile(backdrop, value, FORMAT_VERSION);
    }
}
