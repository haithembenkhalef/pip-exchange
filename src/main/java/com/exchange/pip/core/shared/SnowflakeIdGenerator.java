package com.exchange.pip.core.shared;

/**
 * Snowflake-style 64-bit ID generator: safe across multiple service
 * instances with zero coordination, unlike a plain AtomicLong counter
 * (which only guarantees uniqueness within a single JVM).
 *
 * Layout of the 64-bit long (MSB to LSB):
 *   1 bit  unused (keeps the value positive)
 *  41 bits timestamp (ms since CUSTOM_EPOCH — good for ~69 years)
 *  10 bits node id (0–1023 — one id assigned per running instance)
 *  12 bits per-millisecond sequence (0–4095 ids/ms/node before it waits)
 *
 * IDs are roughly time-ordered (not a strict FIFO guarantee across
 * nodes — clock skew between machines can reorder IDs slightly). If
 * you need strict ordering, that's what Order.sequence (assigned by
 * the single-threaded per-book engine) is for — this class is only
 * about generating a fast, collision-free identifier, not about
 * matching priority.
 *
 * Not needed until you actually run more than one instance of this
 * service. A plain AtomicLong counter (see IdGenerator) is faster and
 * simpler for a single-instance deployment.
 */
public final class SnowflakeIdGenerator {

    private static final long CUSTOM_EPOCH = 1_700_000_000_000L; // arbitrary recent epoch, reduces bit usage

    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 12;

    private static final long MAX_NODE_ID = (1L << NODE_ID_BITS) - 1;     // 1023
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;   // 4095

    private static final int NODE_ID_SHIFT = SEQUENCE_BITS;
    private static final int TIMESTAMP_SHIFT = SEQUENCE_BITS + NODE_ID_BITS;

    private final long nodeId;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long nodeId) {
        if (nodeId < 0 || nodeId > MAX_NODE_ID) {
            throw new IllegalArgumentException("nodeId must be between 0 and " + MAX_NODE_ID);
        }
        this.nodeId = nodeId;
    }

    /**
     * Generates the next unique id. Synchronized: contention only
     * happens between threads on the SAME instance calling this
     * concurrently, which is brief (a few bit-ops) — nowhere near the
     * cost of a DB round trip or distributed coordination.
     */
    public synchronized long next() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp) {
            // Clock moved backwards (NTP correction, VM pause). Refusing
            // to generate an id here is safer than risking a duplicate —
            // silently proceeding could hand out an id that collides
            // with one already generated at that earlier timestamp.
            throw new IllegalStateException(
                "clock moved backwards, refusing to generate id for " + (lastTimestamp - timestamp) + "ms");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                // exhausted this millisecond's 4096 ids — spin until the clock ticks forward
                timestamp = waitForNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - CUSTOM_EPOCH) << TIMESTAMP_SHIFT)
            | (nodeId << NODE_ID_SHIFT)
            | sequence;
    }

    private long waitForNextMillis(long currentTimestamp) {
        long timestamp = System.currentTimeMillis();
        while (timestamp <= currentTimestamp) {
            timestamp = System.currentTimeMillis();
        }
        return timestamp;
    }
}