package dev.ringworld.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.Optional;

/** Server-owned visual presentation of the RingWorld sky and visible light source. */
public record RingSkyProfile(Backdrop backdrop, LightSource lightSource, int formatVersion) {
    public static final int FORMAT_VERSION = 1;
    public static final RingSkyProfile DEFAULT = new RingSkyProfile(
            Backdrop.ATMOSPHERE, LightSource.COMPACT_SUN, FORMAT_VERSION);

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
        ATMOSPHERE(0, "Blue atmosphere"),
        DEEP_SPACE(1, "Deep space"),
        MINIMAL_VOID(2, "Minimal void");

        private final int id;
        private final String label;

        Backdrop(int id, String label) { this.id = id; this.label = label; }
        public int id() { return id; }
        public String label() { return label; }
        public static Backdrop fromId(int id) {
            return Arrays.stream(values()).filter(value -> value.id == id).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown sky backdrop " + id));
        }
    }

    public enum LightSource {
        COMPACT_SUN(0, "Compact sun", 3.0F),
        DISTANT_SUN(1, "Distant star", 15.0F),
        NONE(2, "Diffuse light", 0.0F);

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
        public static LightSource fromId(int id) {
            return Arrays.stream(values()).filter(value -> value.id == id).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown sky light source " + id));
        }
    }

    public enum Preset {
        MINECRAFT_ATMOSPHERE("Atmosphere", Backdrop.ATMOSPHERE, LightSource.COMPACT_SUN),
        SPACE_HABITAT("Space habitat", Backdrop.DEEP_SPACE, LightSource.COMPACT_SUN),
        DISTANT_STAR("Distant star", Backdrop.DEEP_SPACE, LightSource.DISTANT_SUN),
        NIGHT_HABITAT("Night habitat", Backdrop.DEEP_SPACE, LightSource.NONE),
        MINIMAL_VOID("Minimal void", Backdrop.MINIMAL_VOID, LightSource.NONE);

        private final String label;
        private final RingSkyProfile profile;

        Preset(String label, Backdrop backdrop, LightSource source) {
            this.label = label;
            this.profile = new RingSkyProfile(backdrop, source, FORMAT_VERSION);
        }

        public String label() { return label; }
        public RingSkyProfile profile() { return profile; }
        public Preset next() {
            Preset[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
        public static Optional<Preset> find(RingSkyProfile profile) {
            return Arrays.stream(values()).filter(value -> value.profile.equals(profile)).findFirst();
        }
        public static Preset matching(RingSkyProfile profile) {
            return find(profile).orElse(MINECRAFT_ATMOSPHERE);
        }
    }
}
