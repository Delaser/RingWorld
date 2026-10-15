package dev.ringworld.server;

/** Owner-thread measurement model; intentional scheduler parking is the only subtraction. */
final class RingTickWorkAccounting {
    private boolean active;
    private boolean gameFinished;
    private int taskDepth;
    private int idleDepth;
    private boolean countedIdle;
    private long start, gameEnd, idleStart, idle, game, scheduled;

    void begin(long now) {
        if (active) throw new IllegalStateException("unfinished tick measurement");
        active = true; gameFinished = false; taskDepth = idleDepth = 0;
        start = now; idle = game = scheduled = 0;
    }

    void gameFinished(long now) {
        if (!active || gameFinished || now < start) throw new IllegalStateException("invalid game boundary");
        gameEnd = now; game = now - start; gameFinished = true;
    }

    void taskStarted() { if (active) taskDepth++; }
    void taskFinished() {
        if (active && --taskDepth < 0) throw new IllegalStateException("unbalanced task boundary");
    }

    void idleStarted(long now) {
        if (!active) return;
        if (idleDepth++ == 0) {
            countedIdle = gameFinished && taskDepth == 0;
            idleStart = now;
        }
    }

    void idleFinished(long now) {
        if (!active) return;
        if (--idleDepth < 0) throw new IllegalStateException("unbalanced idle boundary");
        if (idleDepth == 0 && countedIdle) {
            if (now < idleStart) throw new IllegalStateException("nonmonotonic idle time");
            idle += now - idleStart;
        }
    }

    void finish(long now) {
        if (!active || !gameFinished || taskDepth != 0 || idleDepth != 0 || now < gameEnd
                || idle > now - gameEnd) throw new IllegalStateException("incomplete tick measurement");
        scheduled = now - gameEnd - idle;
        active = false;
    }

    boolean active() { return active; }
    long startedNanos() { return start; }
    long gameNanos() { return game; }
    long scheduledNanos() { return scheduled; }
    long idleNanos() { return idle; }
}
