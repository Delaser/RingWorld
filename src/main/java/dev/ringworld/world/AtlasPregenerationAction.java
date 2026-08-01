package dev.ringworld.world;

/** Stable, loader-neutral actions exposed by atlas generation adapters. */
public enum AtlasPregenerationAction {
    START(1),
    PAUSE(2),
    RESUME(3),
    CANCEL(4);

    private final int wireValue;
    AtlasPregenerationAction(int wireValue) { this.wireValue = wireValue; }
    public int wireValue() { return wireValue; }

    public static AtlasPregenerationAction fromWireValue(int wireValue) {
        for (AtlasPregenerationAction value : values()) if (value.wireValue == wireValue) return value;
        throw new IllegalArgumentException("unknown atlas action wire value: " + wireValue);
    }
}
