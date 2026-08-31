package dev.ringworld.world;

import com.mojang.serialization.Codec;

/** Immutable macro terrain layout for one RingWorld Overworld. */
public enum RingWorldLayout {
    VANILLA(0, "Vanilla"),
    ARCHIPELAGO(1, "Archipelago");

    public static final Codec<RingWorldLayout> CODEC = Codec.INT.xmap(
            RingWorldLayout::fromId, RingWorldLayout::id);

    private final int id;
    private final String label;

    RingWorldLayout(int id, String label) {
        this.id = id;
        this.label = label;
    }

    public int id() { return id; }
    public String label() { return label; }
    public RingWorldLayout next() {
        RingWorldLayout[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static RingWorldLayout fromId(int id) {
        for (RingWorldLayout value : values()) if (value.id == id) return value;
        throw new IllegalArgumentException("unknown RingWorld layout " + id);
    }
}
