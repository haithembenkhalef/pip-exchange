package com.exchange.pip.core.orderbook;

import com.exchange.pip.core.api.model.ClientOrder;

import java.util.Objects;

/**
 * Represents a single order living in (or about to enter) an order book.
 *
 * Price and quantity are fixed-point integers (smallest tradable unit),
 * never floating point. What "1 unit" means in real-world terms is a
 * symbol-level concern (tick size / lot size), not this class's job.
 *
 * Only remainingQty mutates after construction. Everything else is fixed
 * for the life of the order — if you need a different price, that's a
 * cancel+re-place or a Move operation (Phase 2), not a field mutation.
 */
public final class Order {

    private final long orderId;
    private final long sequence;      // monotonic, assigned at ingestion — used for FIFO tie-break
    private final String symbol;
    private final long userId;
    private final Side side;
    private final OrderType orderType;
    private final long price;         // fixed-point; ignored/irrelevant for MARKET orders
    private final long originalQty;
    private long remainingQty;

    public Order(long orderId, long sequence, String symbol, long userId,
                 Side side, OrderType orderType, long price, long originalQty) {
        if (originalQty <= 0) {
            throw new IllegalArgumentException("originalQty must be positive");
        }
        if (orderType != OrderType.MARKET && price <= 0) {
            throw new IllegalArgumentException("price must be positive for non-market orders");
        }
        this.orderId = orderId;
        this.sequence = sequence;
        this.symbol = symbol;
        this.userId = userId;
        this.side = side;
        this.orderType = orderType;
        this.price = price;
        this.originalQty = originalQty;
        this.remainingQty = originalQty;
    }

    public Order(ClientOrder clientOrder, long sequence) {
        this(
                clientOrder.orderId(),
                sequence,
                clientOrder.symbol(),
                clientOrder.userId(),
                clientOrder.side(),
                clientOrder.orderType(),
                clientOrder.price(),
                clientOrder.quantity()
        );
    }

    public long getOrderId() { return orderId; }
    public long getSequence() { return sequence; }
    public String getSymbol() { return symbol; }
    public long getUserId() { return userId; }
    public Side getSide() { return side; }
    public OrderType getOrderType() { return orderType; }
    public long getPrice() { return price; }
    public long getOriginalQty() { return originalQty; }
    public long getRemainingQty() { return remainingQty; }

    public boolean isFullyFilled() {
        return remainingQty == 0;
    }

    /**
     * Reduces remaining quantity by fillQty. Called only by the matching
     * engine (Phase 1) — never call this from outside the engine package
     * once that logic exists.
     */
    void reduceRemaining(long fillQty) {
        if (fillQty <= 0 || fillQty > remainingQty) {
            throw new IllegalArgumentException(
                "invalid fillQty " + fillQty + " for remaining " + remainingQty);
        }
        remainingQty -= fillQty;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order other)) return false;
        return orderId == other.orderId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    @Override
    public String toString() {
        return "Order{id=%d, seq=%d, symbol=%s, side=%s, type=%s, price=%d, remaining=%d/%d}"
            .formatted(orderId, sequence, symbol, side, orderType, price, remainingQty, originalQty);
    }
}