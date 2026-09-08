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

    private final AtomicLong counter = new AtomicLong(0);

    public long next() {
        return counter.incrementAndGet();
    }
}