package dev.ringworld.world;

import java.util.List;

/** Loader-neutral staged-preview status and compact map-screen grid model. */
public final class RingTerrainPreviewHud {
    private static final int HORIZONTAL_MARGIN = 10;
    private static final int COLUMN_GAP = 8;
    private static final int MAX_COLUMN_WIDTH = 150;

    private RingTerrainPreviewHud() { }

    public static List<Entry> entries(int receivedStage) {
        RingTerrainPreviewStage[] stages = RingTerrainPreviewStage.values();
        if (receivedStage < -1 || receivedStage >= stages.length) {
            throw new IllegalArgumentException("invalid received terrain-preview stage");
        }
        return java.util.stream.IntStream.range(0, stages.length)
                .mapToObj(index -> {
                    RingTerrainPreviewStage stage = stages[index];
                    State state;
                    if (index < receivedStage) state = State.READY;
                    else if (index == receivedStage) state = State.ACTIVE;
                    else if (index == receivedStage + 1) state = State.GENERATING;
                    else state = State.WAITING;
                    return new Entry(stage.displayLabel(), stage.colorColumns(), stage.colorRows(), state);
                })
                .toList();
    }

    public static Grid grid(int screenWidth) {
        int minimum = HORIZONTAL_MARGIN * 2 + COLUMN_GAP + 2;
        if (screenWidth < minimum) throw new IllegalArgumentException("preview screen is too narrow");
        int columnWidth = Math.min(MAX_COLUMN_WIDTH,
                (screenWidth - HORIZONTAL_MARGIN * 2 - COLUMN_GAP) / 2);
        int contentWidth = columnWidth * 2 + COLUMN_GAP;
        int left = (screenWidth - contentWidth) / 2;
        return new Grid(left, left + columnWidth + COLUMN_GAP, columnWidth, screenWidth);
    }

    public record Entry(String name, int columns, int rows, State state) {
        public String label() {
            return name + " " + columns + "x" + rows + ": " + state.label();
        }
    }

    public record Grid(int leftX, int rightX, int columnWidth, int screenWidth) {
        public Grid {
            if (leftX < 0 || rightX <= leftX || columnWidth <= 0
                    || rightX + columnWidth > screenWidth) {
                throw new IllegalArgumentException("invalid preview grid");
            }
        }

        public int xFor(int entryIndex) {
            if (entryIndex < 0 || entryIndex >= RingTerrainPreviewStage.values().length) {
                throw new IllegalArgumentException("invalid preview entry index");
            }
            return (entryIndex & 1) == 0 ? leftX : rightX;
        }
    }

    public enum State {
        WAITING("waiting"),
        GENERATING("generating"),
        ACTIVE("active"),
        READY("ready");

        private final String label;

        State(String label) { this.label = label; }
        public String label() { return label; }
    }
}
