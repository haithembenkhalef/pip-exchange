package com.exchange.pip.core.orderbook;

import com.exchange.pip.core.trade.Trade;

import java.time.Instant;
import java.util.List;

/**
 * The outcome of running one incoming order through the match loop.
 *
 * Immutable snapshot: {@code order} is the same object the engine
 * mutated in place (remainingQty now reflects fills), so callers can
 * read final state from it directly.
 *
 * @param order        the order that was processed (mutated: remainingQty reduced by fills)
 * @param trades       all trades generated, in execution order; empty if the order rested untouched
 * @param restingOnBook true if a non-zero remainder was placed on the book (GTC behavior);
 *                     false if fully filled. (Meaningless for IOC/FOK once they exist — those
 *                     never rest; FOK additionally guarantees fully-filled-or-nothing.)
 */
public record MatchResult(Order order, List<Trade> trades, boolean restingOnBook, long latency) {

    boolean isFullyFilled() {
        return order.isFullyFilled();
    }

    long filledQuantity() {
        return order.getOriginalQty() - order.getRemainingQty();
    }

    long remainingQuantity() {
        return order.getRemainingQty();
    }
}
