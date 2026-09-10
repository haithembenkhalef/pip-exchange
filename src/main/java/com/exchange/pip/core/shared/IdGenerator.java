package com.exchange.pip.core.shared;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Produces strictly increasing sequence numbers.
 *
 * Used for order arrival sequence (FIFO tie-break at equal price) and
 * trade IDs. A sequence counter is preferred over wall-clock timestamps:
 * two orders can arrive within the same clock tick, but they can never
 * receive the same sequence number — which keeps matching deterministic
 * and replayable (important once you build Phase 6 journaling).
 */
public final class IdGenerator {

    private final AtomicLong counter;

    /** Starts fresh at 0 (first call to next() returns 1). */
    public IdGenerator() {
        this(0L);
    }

    /**
     * Starts from a known point — e.g. seeded with a previously-recovered
     * sequence so numbering resumes without gaps or collisions after a
     * restart. First call to next() returns startingValue + 1.
     */
    public IdGenerator(long startingValue) {
        this.counter = new AtomicLong(startingValue);
    }

    public long next() {
        return counter.incrementAndGet();
    }

    /** Current high-water mark without consuming a new value. */
    public long current() {
        return counter.get();
    }
}