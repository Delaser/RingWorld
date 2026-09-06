package dev.ringworld.world;

import com.mojang.serialization.Codec;

/** Server-owned source fidelity and coordinated client render budget. */
public enum RingAtlasFidelity {
    PERFORMANCE(0, "Performance", 16, 2_048, 512, 16),
    BALANCED(1, "Balanced", 8, 4_096, 1_024, 8),
    HIGH(2, "High", 4, 8_192, 1_024, 4),
    VERY_HIGH(3, "Very high", 2, 16_384, 1_024, 4);

    public static final Codec<RingAtlasFidelity> CODEC = Codec.INT.xmap(
            RingAtlasFidelity::fromId, RingAtlasFidelity::id);

    private final int id;
    private final String label;
    private final int sampleStepBlocks;
    private final int maxTextureColumns;
    private final int maxTextureRows;
    private final int meshStepBlocks;

    RingAtlasFidelity(int id, String label, int sampleStepBlocks,
                      int maxTextureColumns, int maxTextureRows, int meshStepBlocks) {
        this.id = id;
        this.label = label;
        this.sampleStepBlocks = sampleStepBlocks;
        this.maxTextureColumns = maxTextureColumns;
        this.maxTextureRows = maxTextureRows;
        this.meshStepBlocks = meshStepBlocks;
    }

    public int id() { return id; }
    public String label() { return label; }
    public int sampleStepBlocks() { return sampleStepBlocks; }
    public int maxTextureColumns() { return maxTextureColumns; }
    public int maxTextureRows() { return maxTextureRows; }
    public int meshStepBlocks() { return meshStepBlocks; }

    public RingAtlasFidelity next() {
        RingAtlasFidelity[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static RingAtlasFidelity fromId(int id) {
        for (RingAtlasFidelity value : values()) if (value.id == id) return value;
        throw new IllegalArgumentException("unknown RingWorld Atlas fidelity " + id);
    }

    public static RingAtlasFidelity forSampleStep(int sampleStepBlocks) {
        for (RingAtlasFidelity value : values()) {
            if (value.sampleStepBlocks == sampleStepBlocks) return value;
        }
        throw new IllegalArgumentException(
                "unsupported RingWorld Atlas sample step " + sampleStepBlocks);
    }
}
