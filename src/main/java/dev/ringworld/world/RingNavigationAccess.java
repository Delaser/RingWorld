package dev.ringworld.world;

/** Keeps an active AI path on the same intrinsic chart when its mob folds. */
public interface RingNavigationAccess {
    void ringworld$foldPath(int deltaX);
}
