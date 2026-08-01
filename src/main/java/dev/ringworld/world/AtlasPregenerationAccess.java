package dev.ringworld.world;

/** Pure permission policy shared by platform-specific player adapters. */
public final class AtlasPregenerationAccess {
    private AtlasPregenerationAccess() { }

    public static boolean canControl(boolean integratedOwner, boolean dedicatedGamemaster) {
        return integratedOwner || dedicatedGamemaster;
    }
}
