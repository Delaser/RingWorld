package dev.ringworld.world;

/** Ordered quality stages for the disposable seed-derived terrain preview. */
public enum RingTerrainPreviewStage {
    CURRENT(0, "current", "Current", 512, 16, 128, 8),
    HIGH(1, "high", "High", 1_024, 32, 256, 16),
    VERY_HIGH(2, "very-high", "Very high", 2_048, 32, 512, 16),
    ULTRA(3, "ultra", "Ultra", 4_096, 64, 1_024, 32);

    private final int wireValue;
    private final String logLabel;
    private final String displayLabel;
    private final int colorColumns;
    private final int colorRows;
    private final int terrainColumns;
    private final int terrainRows;

    RingTerrainPreviewStage(int wireValue, String logLabel, String displayLabel,
                            int colorColumns, int colorRows,
                            int terrainColumns, int terrainRows) {
        this.wireValue = wireValue;
        this.logLabel = logLabel;
        this.displayLabel = displayLabel;
        this.colorColumns = colorColumns;
        this.colorRows = colorRows;
        this.terrainColumns = terrainColumns;
        this.terrainRows = terrainRows;
    }

    public int wireValue() { return wireValue; }
    public String logLabel() { return logLabel; }
    public String displayLabel() { return displayLabel; }
    public int colorColumns() { return colorColumns; }
    public int colorRows() { return colorRows; }
    public int terrainColumns() { return terrainColumns; }
    public int terrainRows() { return terrainRows; }

    public static RingTerrainPreviewStage fromWireValue(int value) {
        for (RingTerrainPreviewStage stage : values()) {
            if (stage.wireValue == value) return stage;
        }
        throw new IllegalArgumentException("unknown terrain-preview stage " + value);
    }
}
