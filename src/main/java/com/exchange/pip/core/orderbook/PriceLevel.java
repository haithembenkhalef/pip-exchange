package com.exchange.pip.core.orderbook;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * All orders resting at a single price, in strict arrival (FIFO) order.
 *
 * Insertion order IS the priority order — we don't need to consult
 * Order.sequence to determine who matches first at this level; append
 * to the tail, always match from the head. sequence stays on Order
 * purely as metadata (see earlier discussion) — it's not consulted here.
 */
final class PriceLevel {

    private final long price;
    private final Deque<Order> orders = new ArrayDeque<>();
    private long totalQuantity = 0;

    PriceLevel(long price) {
        this.price = price;
    }

    long getPrice() {
        return price;
    }

    long getTotalQuantity() {
        return totalQuantity;
    }

    boolean isEmpty() {
        return orders.isEmpty();
    }

    int size() {
        return orders.size();
    }

    /** Appends an order to the back of the queue — it will match last among orders here. */
    void add(Order order) {
        if (order.getPrice() != price) {
            throw new IllegalArgumentException(
                    "order price %d does not match level price %d".formatted(order.getPrice(), price));
        }
        Order lastInQueue = orders.peekLast();
        if (lastInQueue != null && order.getSequence() <= lastInQueue.getSequence()) {
            // Insertion order is supposed to BE arrival order, by construction of the
            // single-threaded pipeline. If this ever fires, some caller violated that
            // invariant (e.g. replay processed out of order, or two threads raced into
            // the same level) — better to fail loudly here than silently corrupt FIFO
            // priority, which would be near-impossible to notice or debug downstream.
            throw new IllegalStateException(
                    "orders added out of sequence: incoming seq=%d <= last queued seq=%d"
                            .formatted(order.getSequence(), lastInQueue.getSequence()));
        }
        orders.addLast(order);
        totalQuantity += order.getRemainingQty();
    }

    /** Returns the order at the front of the queue without removing it, or null if empty. */
    Order peekFirst() {
        return orders.peekFirst();
    }

    /**
     * Removes the order at the front of the queue. Call this only after
     * that order is fully filled — the match loop is responsible for
     * deciding when an order leaves the level.
     */
    void removeFirst() {
        Order removed = orders.pollFirst();
        if (removed != null) {
            totalQuantity -= removed.getRemainingQty();
        }
    }

    /**
     * Removes a specific order from anywhere in the queue (used by cancel,
     * Feature 4 — not needed for the basic match loop, which only ever
     * touches the head).
     */
    boolean remove(Order order) {
        boolean removed = orders.remove(order);
        if (removed) {
            totalQuantity -= order.getRemainingQty();
        }
        return removed;
    }

    /**
     * Call after reducing an order's remaining quantity via a partial fill,
     * to keep totalQuantity accurate. The order stays at the head of the
     * queue — it does NOT lose priority from a partial fill.
     */
    void onPartialFill(long filledQty) {
        totalQuantity -= filledQty;
    }
}