package dev.ringworld.world;

/** Local display choices. These IDs are not saved generation settings or network values. */
public enum RingLodQuality {
    LOW("low", 8, 4096, 1024, 8),
    MEDIUM("medium", 2, 16384, 1024, 4),
    HIGH("high", 1, 32768, 2048, 1);

    private final String command;
    private final int sampleStep, columns, rows, meshStep;
    RingLodQuality(String command, int sampleStep, int columns, int rows, int meshStep) {
        this.command = command; this.sampleStep = sampleStep;
        this.columns = columns; this.rows = rows; this.meshStep = meshStep;
    }
    public String command() { return command; }
    public String label() { return command.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + command.substring(1); }
    public RingLodQuality next() { return values()[(ordinal() + 1) % values().length]; }
    public int sampleStep() { return sampleStep; }
    public int meshStep() { return meshStep; }
    public RingRenderProfile profile(RingGeometry geometry, double distance) {
        return profile(geometry, distance, Integer.MAX_VALUE);
    }
    public RingRenderProfile profile(RingGeometry geometry, double distance, int textureLimit) {
        return RingRenderProfile.create(geometry, distance,
                Math.min(columns, textureLimit), Math.min(rows, textureLimit), meshStep);
    }
    /** Retains captured colour/height anchors and averages water coverage over each footprint. */
    public RingTerrainAtlas displaySnapshot(RingTerrainAtlas source) {
        if (!source.isComplete() || sampleStep <= source.sampleStep()) return source.snapshot();
        var result = new RingTerrainAtlas(source.geometry(), source.worldHash(), sampleStep);
        for (int row = 0; row < result.rows(); row++) {
            int sourceRow = Math.min(source.rows() - 1, (row * sampleStep + sampleStep / 2) / source.sampleStep());
            for (int col = 0; col < result.columns(); col++) {
                int sourceCol = Math.min(source.columns() - 1, (col * sampleStep + sampleStep / 2) / source.sampleStep());
                result.putCell(col, row, source.cellHeight(sourceCol, sourceRow),
                        source.cellColor(sourceCol, sourceRow), source.cellBlockLight(sourceCol, sourceRow),
                        source.cellSideColor(sourceCol, sourceRow), waterCoverage(source, col, row));
            }
        }
        return result;
    }

    private int waterCoverage(RingTerrainAtlas source, int column, int row) {
        int scale = sampleStep / source.sampleStep();
        int sum = 0, count = 0;
        for (int z = row * scale; z < Math.min(source.rows(), (row + 1) * scale); z++) {
            for (int x = column * scale; x < Math.min(source.columns(), (column + 1) * scale); x++) {
                sum += source.cellWaterCoverage(x, z);
                count++;
            }
        }
        return count == 0 ? 0 : (int)Math.round((double)sum / count);
    }
}
