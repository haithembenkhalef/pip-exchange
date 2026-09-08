package com.exchange.pip.core.orderbook;

import java.util.Comparator;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * A single symbol's order book: two sorted collections of PriceLevels.
 *
 * Bids sorted highest-price-first (best bid = first entry).
 * Asks sorted lowest-price-first (best ask = first entry).
 *
 * This is the "Naive" implementation (TreeMap-backed) — correctness
 * first. A "Direct" (radix-tree/array) implementation can replace the
 * internals later without changing this class's public contract.
 *
 * IMPORTANT: this class has NO matching logic yet. addOrder() only
 * rests an order — it does not check whether it crosses the opposite
 * side. Matching is added in the next feature, layered on top of this
 * structure once the structure itself is verified correct.
 */
final class OrderBook {

    private final String symbol;

    // Bids: highest price first -> reverse natural ordering
    private final NavigableMap<Long, PriceLevel> bids = new TreeMap<>(Comparator.reverseOrder());
    // Asks: lowest price first -> natural ordering
    private final NavigableMap<Long, PriceLevel> asks = new TreeMap<>();

    // orderId -> order, for O(log n) cancel/lookup without scanning every level
    private final Map<Long, Order> ordersById = new java.util.HashMap<>();

    OrderBook(String symbol) {
        this.symbol = symbol;
    }

    String getSymbol() {
        return symbol;
    }

    /**
     * Rests an order on the book at its limit price, with no matching.
     * Matching engine logic (Feature 3) will call this only for the
     * remainder of an order after attempting to match it — for now,
     * it's used directly so we can test the structure in isolation.
     */
    void restOrder(Order order) {
        if (ordersById.containsKey(order.getOrderId())) {
            throw new IllegalStateException("duplicate orderId: " + order.getOrderId());
        }
        NavigableMap<Long, PriceLevel> side = order.getSide() == Side.BID ? bids : asks;
        PriceLevel level = side.computeIfAbsent(order.getPrice(), PriceLevel::new);
        level.add(order);
        ordersById.put(order.getOrderId(), order);
    }

    /** Best bid price, or null if no bids resting. */
    Long getBestBidPrice() {
        Map.Entry<Long, PriceLevel> entry = bids.firstEntry();
        return entry == null ? null : entry.getKey();
    }

    /** Best ask price, or null if no asks resting. */
    Long getBestAskPrice() {
        Map.Entry<Long, PriceLevel> entry = asks.firstEntry();
        return entry == null ? null : entry.getKey();
    }

    /** Returns the PriceLevel at the top of the bid side, or null if empty. */
    PriceLevel getBestBidLevel() {
        Map.Entry<Long, PriceLevel> entry = bids.firstEntry();
        return entry == null ? null : entry.getValue();
    }

    /** Returns the PriceLevel at the top of the ask side, or null if empty. */
    PriceLevel getBestAskLevel() {
        Map.Entry<Long, PriceLevel> entry = asks.firstEntry();
        return entry == null ? null : entry.getValue();
    }

    /** Looks up a resting order by ID, or null if not found (already filled/cancelled/never existed). */
    Order findOrder(long orderId) {
        return ordersById.get(orderId);
    }

    int totalRestingOrders() {
        return ordersById.size();
    }

    NavigableMap<Long, PriceLevel> getBidLevels() {
        return bids;
    }

    NavigableMap<Long, PriceLevel> getAskLevels() {
        return asks;
    }

    /**
     * Called by the match loop after it has confirmed the order at the
     * head of a level is fully filled and already popped via
     * PriceLevel.removeFirst(). This only updates OrderBook-level
     * bookkeeping (the orderId index, and removing the level itself if
     * it's now empty) — it does NOT touch the PriceLevel's queue, since
     * the caller already did that.
     */
    void onHeadOrderFullyFilled(Order order) {
        ordersById.remove(order.getOrderId());
        cleanUpLevelIfEmpty(order.getSide(), order.getPrice());
    }

    /**
     * Removes an order from the book entirely, wherever it sits in its
     * price level's queue — not just the head. Used by cancel (Feature 4)
     * and useful directly in tests of this structural layer. Returns
     * false if the order isn't found (already filled/cancelled/unknown).
     */
    boolean removeOrder(long orderId) {
        Order order = ordersById.get(orderId);
        if (order == null) {
            return false;
        }
        NavigableMap<Long, PriceLevel> side = order.getSide() == Side.BID ? bids : asks;
        PriceLevel level = side.get(order.getPrice());
        if (level == null || !level.remove(order)) {
            // structural inconsistency: order was indexed but not in its level.
            // Should never happen if restOrder/onHeadOrderFullyFilled are the
            // only mutators — fail loudly rather than silently drift.
            throw new IllegalStateException(
                "order %d indexed but not found in its price level".formatted(orderId));
        }
        ordersById.remove(orderId);
        cleanUpLevelIfEmpty(order.getSide(), order.getPrice());
        return true;
    }

    private void cleanUpLevelIfEmpty(Side side, long price) {
        NavigableMap<Long, PriceLevel> map = side == Side.BID ? bids : asks;
        PriceLevel level = map.get(price);
        if (level != null && level.isEmpty()) {
            map.remove(price);
        }
    }
}