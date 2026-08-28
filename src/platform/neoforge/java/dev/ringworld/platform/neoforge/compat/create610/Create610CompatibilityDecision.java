package dev.ringworld.platform.neoforge.compat.create610;

import java.util.Objects;

/** Pure exact-tuple activation decision for the NeoForge Create adapter. */
public final class Create610CompatibilityDecision {
    public static final String MINECRAFT_VERSION = "1.21.1";
    public static final String NEOFORGE_VERSION = "21.1.239";
    public static final String CREATE_VERSION = "6.0.10";
    public static final String FLYWHEEL_VERSION = "1.0.6";

    private Create610CompatibilityDecision() { }

    public static Result evaluate(String minecraftVersion, String neoForgeVersion,
                                  String createVersion, String flywheelVersion) {
        if (createVersion == null) return new Result(State.CREATE_ABSENT, null);
        boolean exact = Objects.equals(MINECRAFT_VERSION, minecraftVersion)
                && Objects.equals(NEOFORGE_VERSION, neoForgeVersion)
                && Objects.equals(CREATE_VERSION, createVersion)
                && Objects.equals(FLYWHEEL_VERSION, flywheelVersion);
        if (exact) return new Result(State.EXACT, null);
        return new Result(State.UNQUALIFIED, "Minecraft=" + shown(minecraftVersion)
                + ", NeoForge=" + shown(neoForgeVersion)
                + ", Create=" + shown(createVersion)
                + ", Flywheel=" + shown(flywheelVersion));
    }

    private static String shown(String value) {
        return value == null ? "absent" : value;
    }

    public enum State {
        CREATE_ABSENT,
        UNQUALIFIED,
        EXACT
    }

    public record Result(State state, String observedTuple) {
        public boolean enabled() {
            return state == State.EXACT;
        }
    }
}
