package com.exchange.pip.core.trade;

/**
 * Emitted by the matching engine every time two orders match.
 *
 * This is the seam between matching logic and everything downstream:
 * balance settlement, market data / trade history, journaling all
 * consume Trade events rather than reaching into order book internals.
 *
 * Convention: execution price is always the MAKER's price (the order
 * that was already resting on the book) — the taker gets price
 * improvement if the market moved in their favor.
 */
public record Trade(
    long tradeId,
    String symbol,
    long price,
    long quantity,
    long makerOrderId,
    long makerUserId,
    long takerOrderId,
    long takerUserId,
    long sequence
) {
    public Trade {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (price <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
    }
}
