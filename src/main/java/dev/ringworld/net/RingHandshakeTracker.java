package dev.ringworld.net;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Loader-neutral server-thread state machine for the mandatory geometry
 * acknowledgement. Network adapters own disconnects and payload delivery.
 */
public final class RingHandshakeTracker {
    public static final long ACK_TIMEOUT_TICKS = 300L;

    private final Map<UUID, Long> pendingDeadlines = new HashMap<>();
    private final Set<UUID> acknowledged = new HashSet<>();

    public void begin(UUID playerId, long currentTick) {
        acknowledged.remove(playerId);
        pendingDeadlines.put(playerId, Math.addExact(currentTick, ACK_TIMEOUT_TICKS));
    }

    public AcknowledgementResult acknowledge(UUID playerId) {
        if (pendingDeadlines.remove(playerId) != null) {
            acknowledged.add(playerId);
            return AcknowledgementResult.ACCEPTED;
        }
        return acknowledged.contains(playerId)
                ? AcknowledgementResult.ALREADY_ACKNOWLEDGED
                : AcknowledgementResult.UNEXPECTED;
    }

    public boolean isAcknowledged(UUID playerId) {
        return acknowledged.contains(playerId);
    }

    public Set<UUID> expire(long currentTick) {
        Set<UUID> expired = new HashSet<>();
        Iterator<Map.Entry<UUID, Long>> iterator = pendingDeadlines.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            if (currentTick < entry.getValue()) continue;
            expired.add(entry.getKey());
            iterator.remove();
        }
        return Set.copyOf(expired);
    }

    public void clear(UUID playerId) {
        pendingDeadlines.remove(playerId);
        acknowledged.remove(playerId);
    }

    public enum AcknowledgementResult {
        ACCEPTED,
        ALREADY_ACKNOWLEDGED,
        UNEXPECTED
    }
}
