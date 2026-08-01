package dev.ringworld.world;

/** Internal bridge for server-owned position changes that bypass movement packets. */
public interface RingPlayerMovementAccess {
    void ringworld$resetPlayerMovementBaselines();
}
