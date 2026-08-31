package dev.ringworld.world;

import java.util.List;

/** Debug view of the staged seed-preview textures received by the client. */
public final class RingTerrainPreviewHud {
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

    public record Entry(String name, int columns, int rows, State state) {
        public String label() {
            return "Preview " + name + " " + columns + "x" + rows + ": "
                    + state.label();
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
