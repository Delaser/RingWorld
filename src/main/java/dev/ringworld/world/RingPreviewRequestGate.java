package dev.ringworld.world;

/** Owns publication for disposable, asynchronous creation previews. */
public final class RingPreviewRequestGate<T> {
    private long currentRequest;
    private T completed;

    /** Starts a new request and discards any result from an earlier one. */
    public synchronized long begin() {
        completed = null;
        return ++currentRequest;
    }

    /** Publishes only if this worker still belongs to the current request. */
    public synchronized void complete(long request, T result) {
        if (request == currentRequest) completed = result;
    }

    /** Returns and clears the one result accepted for the current request. */
    public synchronized T poll() {
        T result = completed;
        completed = null;
        return result;
    }

    /** True when a worker may still publish its result. */
    public synchronized boolean isCurrent(long request) {
        return request == currentRequest;
    }
}
