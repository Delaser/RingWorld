package dev.ringworld.server;

import dev.ringworld.world.RingSkyProfile;
import java.util.Locale;

/** Pure parsing and profile-update policy for the live server sky commands. */
public final class RingSkyCommandModel {
    private RingSkyCommandModel() { }

    public static RingSkyProfile.Backdrop parseBackdrop(String name) {
        if (name == null) throw new IllegalArgumentException("sky backdrop is required");
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "atmosphere" -> RingSkyProfile.Backdrop.ATMOSPHERE;
            case "night" -> RingSkyProfile.Backdrop.NIGHT;
            case "void" -> RingSkyProfile.Backdrop.VOID;
            default -> throw new IllegalArgumentException(
                    "Unknown sky. Use atmosphere, night, or void.");
        };
    }

    public static RingSkyProfile.LightSource parseLightSource(String name) {
        if (name == null) throw new IllegalArgumentException("sun style is required");
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "small" -> RingSkyProfile.LightSource.SMALL;
            case "large" -> RingSkyProfile.LightSource.LARGE;
            case "none" -> RingSkyProfile.LightSource.NONE;
            default -> throw new IllegalArgumentException(
                    "Unknown sun style. Use small, large, or none.");
        };
    }

    public static RingSkyProfile withBackdrop(
            RingSkyProfile current, RingSkyProfile.Backdrop backdrop) {
        if (current == null) throw new IllegalArgumentException("current sky profile is required");
        return current.withBackdrop(backdrop);
    }

    public static RingSkyProfile withLightSource(
            RingSkyProfile current, RingSkyProfile.LightSource lightSource) {
        if (current == null) throw new IllegalArgumentException("current sky profile is required");
        return current.withLightSource(lightSource);
    }
}
