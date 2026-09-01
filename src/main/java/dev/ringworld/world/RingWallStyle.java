package dev.ringworld.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.Optional;

/** Immutable, loader-neutral appearance policy for both finite rim walls. */
public record RingWallStyle(int thicknessBlocks, Palette palette, Pattern pattern,
                            int decayPercent, int formatVersion) {
    public static final int FORMAT_VERSION = 1;
    public static final int MIN_THICKNESS = 1;
    public static final int MAX_THICKNESS = 32;

    /** Exact appearance used by worlds created before configurable walls. */
    public static final RingWallStyle LEGACY = new RingWallStyle(
            5, Palette.WEATHERED, Pattern.CLUSTERED, 0, FORMAT_VERSION);
    public static final RingWallStyle DEFAULT = new RingWallStyle(
            5, Palette.WEATHERED, Pattern.MASONRY, 25, FORMAT_VERSION);

    public static final Codec<RingWallStyle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("thickness").forGetter(RingWallStyle::thicknessBlocks),
            Codec.INT.fieldOf("palette").xmap(Palette::fromId, Palette::id)
                    .forGetter(RingWallStyle::palette),
            Codec.INT.fieldOf("pattern").xmap(Pattern::fromId, Pattern::id)
                    .forGetter(RingWallStyle::pattern),
            Codec.INT.fieldOf("decay").forGetter(RingWallStyle::decayPercent),
            Codec.INT.fieldOf("format").forGetter(RingWallStyle::formatVersion)
    ).apply(instance, RingWallStyle::new));

    public RingWallStyle {
        if (thicknessBlocks < MIN_THICKNESS || thicknessBlocks > MAX_THICKNESS) {
            throw new IllegalArgumentException("rim thickness must be between "
                    + MIN_THICKNESS + " and " + MAX_THICKNESS + " blocks");
        }
        if (palette == null) throw new IllegalArgumentException("rim palette is required");
        if (pattern == null) throw new IllegalArgumentException("rim pattern is required");
        if (decayPercent < 0 || decayPercent > 100) {
            throw new IllegalArgumentException("rim decay must be between 0 and 100 percent");
        }
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("unsupported rim style format " + formatVersion);
        }
    }

    public static RingWallStyle custom(int thicknessBlocks, Palette palette,
                                       Pattern pattern, int decayPercent) {
        return new RingWallStyle(thicknessBlocks, palette, pattern, decayPercent, FORMAT_VERSION);
    }

    public String conciseLabel() {
        return palette.label() + " · " + pattern.label() + " · " + decayPercent + "%";
    }

    public enum Palette {
        WEATHERED(0, "Weathered stone", "Cobble, mossy cobble, stone, andesite"),
        ANCIENT(1, "Ancient masonry", "Stone brick, cracked, mossy, cobble"),
        NATURAL(2, "Natural rock", "Stone, tuff, andesite, cobble, moss"),
        ALLOY(3, "Ring alloy", "Smooth stone, diorite, quartz, prismarine"),
        INDUSTRIAL(4, "Industrial", "Deepslate, basalt, copper, 0.1% lanterns"),
        OVERGROWN(5, "Overgrown ruin", "Stone brick, mossy, cracked, moss"),
        MONOLITH(6, "Clean monolith", "Smooth stone, calcite, polished andesite"),
        NETHER(7, "Nether fortress", "Nether brick, red brick, blackstone, magma"),
        OBSIDIAN(8, "Obsidian bastion", "Obsidian, crying, blackstone, amethyst"),
        WOOD(9, "Timber rampart", "Oak, spruce, dark oak, stripped timber");

        private final int id;
        private final String label;
        private final String materials;

        Palette(int id, String label, String materials) {
            this.id = id;
            this.label = label;
            this.materials = materials;
        }

        public int id() { return id; }
        public String label() { return label; }
        public String materials() { return materials; }

        public Palette next() {
            Palette[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public static Palette fromId(int id) {
            return Arrays.stream(values()).filter(value -> value.id == id).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown rim palette " + id));
        }
    }

    public enum Pattern {
        CLUSTERED(0, "Clustered"),
        MASONRY(1, "Masonry"),
        STRATA(2, "Strata"),
        PANELS(3, "Panels & ribs"),
        GRADIENT(4, "Gradient"),
        HYBRID(5, "Hybrid");

        private final int id;
        private final String label;

        Pattern(int id, String label) {
            this.id = id;
            this.label = label;
        }

        public int id() { return id; }
        public String label() { return label; }

        /** Patterns offered for new worlds. Retired IDs remain decodable for old saves. */
        public static Pattern[] selectableValues() {
            return new Pattern[] { MASONRY, PANELS, GRADIENT, HYBRID };
        }

        public Pattern next() {
            Pattern[] selectable = selectableValues();
            for (int index = 0; index < selectable.length; index++) {
                if (selectable[index] == this) {
                    return selectable[(index + 1) % selectable.length];
                }
            }
            return MASONRY;
        }

        public static Pattern fromId(int id) {
            return Arrays.stream(values()).filter(value -> value.id == id).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unknown rim pattern " + id));
        }
    }

    public enum Preset {
        WEATHERED_FORTIFICATION("Weathered", 5, Palette.WEATHERED, Pattern.MASONRY, 25),
        ANCIENT_MASONRY("Ancient", 6, Palette.ANCIENT, Pattern.MASONRY, 40),
        NATURAL_ESCARPMENT("Escarpment", 8, Palette.NATURAL, Pattern.GRADIENT, 15),
        RING_ALLOY("Ring alloy", 5, Palette.ALLOY, Pattern.PANELS, 5),
        INDUSTRIAL_SUPERSTRUCTURE("Industrial", 7, Palette.INDUSTRIAL, Pattern.PANELS, 10),
        OVERGROWN_RUIN("Overgrown", 6, Palette.OVERGROWN, Pattern.HYBRID, 70),
        CLEAN_MONOLITH("Monolith", 4, Palette.MONOLITH, Pattern.PANELS, 0),
        NETHER_FORTRESS("Nether", 7, Palette.NETHER, Pattern.MASONRY, 25),
        OBSIDIAN_BASTION("Obsidian", 5, Palette.OBSIDIAN, Pattern.PANELS, 8),
        TIMBER_RAMPART("Wood", 4, Palette.WOOD, Pattern.PANELS, 20);

        private final String label;
        private final RingWallStyle style;

        Preset(String label, int thickness, Palette palette, Pattern pattern, int decay) {
            this.label = label;
            this.style = new RingWallStyle(thickness, palette, pattern, decay, FORMAT_VERSION);
        }

        public String label() { return label; }
        public RingWallStyle style() { return style; }
        public String descriptor() {
            return style.thicknessBlocks() + " thick · " + style.pattern().label()
                    + " · " + style.decayPercent() + "% decay";
        }

        public String materials() { return style.palette().materials(); }

        public Preset next() {
            Preset[] values = values();
            return values[(ordinal() + 1) % values.length];
        }

        public static Preset matching(RingWallStyle style) {
            return find(style).orElse(WEATHERED_FORTIFICATION);
        }

        public static Optional<Preset> find(RingWallStyle style) {
            return Arrays.stream(values()).filter(value -> value.style.equals(style)).findFirst();
        }
    }
}
