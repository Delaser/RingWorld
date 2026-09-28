package dev.ringworld.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Immutable optional world-generation choices selected before first load. */
public record RingWorldGenerationSettings(
        RingAtlasFidelity atlasFidelity,
        RingWorldLayout layout,
        boolean continuousRiver,
        boolean moreStructures,
        int formatVersion) {
    public static final int FORMAT_VERSION = 1;
    public static final RingWorldGenerationSettings DEFAULT = new RingWorldGenerationSettings(
            RingAtlasFidelity.BALANCED, RingWorldLayout.VANILLA, false, false, FORMAT_VERSION);
    public static final Codec<RingWorldGenerationSettings> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    RingAtlasFidelity.CODEC.fieldOf("atlas_fidelity")
                            .forGetter(RingWorldGenerationSettings::atlasFidelity),
                    RingWorldLayout.CODEC.fieldOf("layout")
                            .forGetter(RingWorldGenerationSettings::layout),
                    Codec.BOOL.fieldOf("continuous_river")
                            .forGetter(RingWorldGenerationSettings::continuousRiver),
                    Codec.BOOL.fieldOf("more_structures")
                            .forGetter(RingWorldGenerationSettings::moreStructures),
                    Codec.INT.fieldOf("format")
                            .forGetter(RingWorldGenerationSettings::formatVersion)
            ).apply(instance, RingWorldGenerationSettings::new));

    public RingWorldGenerationSettings {
        if (atlasFidelity == null) throw new IllegalArgumentException("Atlas fidelity is required");
        if (layout == null) throw new IllegalArgumentException("world layout is required");
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported RingWorld generation-settings format " + formatVersion);
        }
    }

    public RingWorldGenerationSettings withAtlasFidelity(RingAtlasFidelity fidelity) {
        return new RingWorldGenerationSettings(
                fidelity, layout, continuousRiver, moreStructures, FORMAT_VERSION);
    }

    public RingWorldGenerationSettings withLayout(RingWorldLayout replacement) {
        return new RingWorldGenerationSettings(
                atlasFidelity, replacement, continuousRiver, moreStructures, FORMAT_VERSION);
    }

    public RingWorldGenerationSettings withContinuousRiver(boolean enabled) {
        return new RingWorldGenerationSettings(
                atlasFidelity, layout, enabled, moreStructures, FORMAT_VERSION);
    }

    public RingWorldGenerationSettings withMoreStructures(boolean enabled) {
        return new RingWorldGenerationSettings(
                atlasFidelity, layout, continuousRiver, enabled, FORMAT_VERSION);
    }
}
