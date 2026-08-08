package dev.ringworld.world;

import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

/** Named, loader-neutral layouts used by the custom-dimension acceptance matrix. */
final class RingDimensionFixtures {
    static final Layout SMALL = new Layout("small", 2_048, 128, 160);
    static final Layout SAFE_SMALL = new Layout("safe-small", 2_048, 416, 160);
    static final Layout MINIMUM_PLAYABLE = new Layout("minimum-playable", 2_016, 256, 160);
    static final Layout NARROW_SAFE_SMALL = new Layout("narrow-safe-small", 2_048, 256, 160);
    static final Layout MEDIUM = new Layout("medium", 16_384, 256, 160);
    static final Layout PRODUCTION = MEDIUM;
    static final Layout FORMER_WIDE = new Layout("former-wide", 15_552, 4_096, 160);
    static final Layout LARGE = new Layout("large", 32_768, 512, 160);
    static final Layout LONG_NARROW = LARGE;
    static final Layout WIDE_MEDIUM = new Layout("wide-medium", 4_096, 2_048, 160);
    static final Layout CUSTOM_WALL = new Layout("custom-wall", 4_096, 640, 192);

    private RingDimensionFixtures() { }

    static Stream<Arguments> playableLayouts() {
        return Stream.of(SMALL, SAFE_SMALL, MINIMUM_PLAYABLE, NARROW_SAFE_SMALL, PRODUCTION,
                        FORMER_WIDE, LARGE, WIDE_MEDIUM, CUSTOM_WALL)
                .map(Layout::arguments);
    }

    static Stream<Arguments> visualLayouts() {
        return Stream.of(SMALL, SAFE_SMALL, NARROW_SAFE_SMALL, PRODUCTION, FORMER_WIDE,
                        LARGE, WIDE_MEDIUM, CUSTOM_WALL)
                .flatMap(layout -> Stream.of(6, 12, 28, 64)
                        .map(viewDistance -> layout.arguments(viewDistance)));
    }

    record Layout(String name, int circumference, int width, int wallHeight) {
        Arguments arguments() {
            return Arguments.of(name, circumference, width, wallHeight);
        }

        Arguments arguments(int viewDistanceChunks) {
            return Arguments.of(name, circumference, width, wallHeight, viewDistanceChunks);
        }
    }
}
