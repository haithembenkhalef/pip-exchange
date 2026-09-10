package com.exchange.pip.core.orderbook;

import com.exchange.pip.core.api.model.ClientOrder;
import com.exchange.pip.core.orderbook.events.OrderEvent;
import com.exchange.pip.core.shared.IdGenerator;
import com.exchange.pip.core.trade.Trade;
import com.lmax.disruptor.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * The naive price-time priority match loop (GTC orders only — for now).
 *
 * For each incoming (taker) order:
 *   1. Walk the opposite side best-first (asks ascending for a bid,
 *      bids descending for an ask), consuming resting (maker) orders
 *      from the head of each price level in strict FIFO order.
 *   2. A price level is reachable only while it crosses the taker's
 *      limit. Execution price is always the MAKER's price.
 *   3. A maker that fills completely leaves the book (level head is
 *      popped + OrderBook bookkeeping). A partially filled maker keeps
 *      its queue position — partial fill does not lose priority.
 *   4. Whatever remains un-filled after the sweep is rested on the
 *      book (GTC semantics).
 *
 * Deliberately NOT handled here yet (later features):
 *   - IOC / FOK / MARKET order types (rejected loudly below)
 *   - Self-trade prevention (taker matching against its own user's orders)
 *   - Balance/hold validation (assumed done upstream before the engine)
 *   - Fee calculation, market data publication, journaling
 *
 * Threading contract: like OrderBook, this engine assumes a single
 * caller at a time (the single-threaded command pipeline). It keeps no
 * state of its own besides the ID generator.
 */
public final class MatchingEngine {

    Logger logger = LoggerFactory.getLogger(MatchingEngine.class);

    private final OrderBook book;
    private final IdGenerator tradeIdGenerator;

    MatchingEngine(OrderBook book, IdGenerator tradeIdGenerator) {
        this.book = book;
        this.tradeIdGenerator = tradeIdGenerator;
    }

    /** Convenience constructor: engine with its own fresh trade-id sequence. */
    MatchingEngine(OrderBook book) {
        this(book, new IdGenerator());
    }

    public MatchResult handleOrderEvent(OrderEvent event) {
        ClientOrder order = event.getOrder();
        Order take = new Order(order, event.getSequence());
        MatchResult result = processOrder(take);
        return result;
    }

    /**
     * Runs one order through the book. The order object is MUTATED in
     * place (remainingQty reduced by each fill) — do not reuse an Order
     * instance for a second call; construct a fresh one per command.
     */
    MatchResult processOrder(Order taker) {

        long startTime = System.nanoTime();
        if (taker.getOrderType() != OrderType.GTC) {
            // Naive phase: GTC only. IOC/FOK/MARKET branch off the same
            // loop later — fail loudly rather than silently resting an
            // IOC order, which would be a silent correctness bug.
            throw new UnsupportedOperationException(
                    "order type %s not supported yet (GTC only)".formatted(taker.getOrderType()));
        }

        List<Trade> trades = new ArrayList<>();

        while (!taker.isFullyFilled()) {
            PriceLevel bestLevel = bestOppositeLevel(taker.getSide());
            if (bestLevel == null || !crosses(taker, bestLevel.getPrice())) {
                break; // book exhausted or no longer crossing — stop sweeping
            }

            Order maker = bestLevel.peekFirst();
            long fillQty = Math.min(taker.getRemainingQty(), maker.getRemainingQty());
            long execPrice = maker.getPrice(); // maker's price, always

            long tradeSeq = tradeIdGenerator.next();
            trades.add(new Trade(
                    tradeSeq,               // tradeId
                    book.getSymbol(),
                    execPrice,
                    fillQty,
                    maker.getOrderId(),
                    maker.getUserId(),
                    taker.getOrderId(),
                    taker.getUserId(),
                    tradeSeq));

            // Mutate both sides. Order.reduceRemaining validates bounds,
            // so an over-fill would throw instead of corrupting state.
            maker.reduceRemaining(fillQty);
            taker.reduceRemaining(fillQty);

            if (maker.isFullyFilled()) {
                bestLevel.removeFirst();                        // pop the head
                book.onHeadOrderFullyFilled(maker);             // drop index + empty level
            } else {
                bestLevel.onPartialFill(fillQty);               // maker keeps its priority
            }
        }

        boolean resting = !taker.isFullyFilled();
        if (resting) {
            book.restOrder(taker); // GTC: remainder rests at its limit price
        }
        long endTime = System.nanoTime();
        MatchResult matchResult = new MatchResult(taker, trades, resting, endTime - startTime);
        //logger.debug("Match result is {}", matchResult);

        return matchResult;
    }

    /** Best reachable opposite-side level, or null if that side is empty. */
    private PriceLevel bestOppositeLevel(Side takerSide) {
        return takerSide == Side.BID ? book.getBestAskLevel() : book.getBestBidLevel();
    }

    /**
     * Does an incoming order cross (i.e., is it willing to trade at) the
     * given resting price? MARKET orders would always cross — the check
     * is written so they slot in later without touching the loop.
     */
    private boolean crosses(Order taker, long restingPrice) {
        return switch (taker.getSide()) {
            case BID -> taker.getOrderType() == OrderType.MARKET || taker.getPrice() >= restingPrice;
            case ASK -> taker.getOrderType() == OrderType.MARKET || taker.getPrice() <= restingPrice;
        };
    }

    public void clearBook() {
        book.cleanUp();
    }
}
