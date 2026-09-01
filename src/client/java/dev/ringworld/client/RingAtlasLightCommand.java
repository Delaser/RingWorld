package dev.ringworld.client;

import dev.ringworld.world.RingAtlasLightProfile;

/** Loader-neutral execution and feedback model for the process-local light command. */
public final class RingAtlasLightCommand {
    private static final String PREFIX = "Ring Atlas light: ";

    private RingAtlasLightCommand() { }

    public static Result show() {
        return success(RingAtlasLightTuning.profile());
    }

    public static Result reset() {
        return success(RingAtlasLightTuning.reset());
    }

    public static Result tune(float falloff, float peak) {
        try {
            return success(RingAtlasLightTuning.useGamma(falloff, peak));
        } catch (IllegalArgumentException exception) {
            return new Result(false, "Ring Atlas light error: " + exception.getMessage());
        }
    }

    private static Result success(RingAtlasLightProfile profile) {
        return new Result(true, PREFIX + profile.summary());
    }

    public record Result(boolean success, String message) {
        public Result {
            if (message == null || message.isBlank()) {
                throw new IllegalArgumentException("command feedback is required");
            }
        }
    }
}
