package dev.ringworld.client.render;

import dev.ringworld.client.ClientRingState;
import dev.ringworld.world.RingDimensionReport;
import net.minecraft.client.Minecraft;

/** Exact inverse of 1.21.1's cloud mesh scale/translation for the curved-cloud shader. */
public final class RingCloudShaderState {
    private static float cellX;
    private static float cellY;
    private static float cellZ;

    private RingCloudShaderState() { }

    public static void update(float tickProgress, double cameraX,
                              double cameraY, double cameraZ) {
        Minecraft client = Minecraft.getInstance();
        if (ClientRingState.geometry() == null || client.level == null) {
            cellX = cellY = cellZ = 0.0F;
            return;
        }
        double drift = (client.level.getGameTime() + tickProgress) * 0.03;
        double x = (cameraX + drift) / 12.0;
        double cloudHeight = cloudBaseY();
        double y = cloudHeight - cameraY + 0.33;
        double z = cameraZ / 12.0 + 0.33;
        x -= Math.floor(x / 2048.0) * 2048.0;
        z -= Math.floor(z / 2048.0) * 2048.0;
        cellX = (float)(x - Math.floor(x));
        cellY = (float)(y / 4.0 - Math.floor(y / 4.0)) * 4.0F;
        cellZ = (float)(z - Math.floor(z));
    }

    public static float cloudBaseY() {
        Minecraft client = Minecraft.getInstance();
        int bottom = client.level == null
                ? RingDimensionReport.VANILLA_OVERWORLD_BOTTOM_Y
                : client.level.getMinBuildHeight();
        return bottom + ClientRingState.wallHeightBlocks()
                + RingDimensionReport.CLOUD_CLEARANCE_BLOCKS;
    }

    public static float cellX() { return cellX; }
    public static float cellY() { return cellY; }
    public static float cellZ() { return cellZ; }
}
