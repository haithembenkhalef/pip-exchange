package com.exchange.pip.core.orderbook;

import com.exchange.pip.core.trade.Trade;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the naive GTC match loop.
 *
 * Test prices are small integers; read them as "ticks", not dollars.
 * sequence numbers are handed out manually (100, 200, ...) instead of
 * via IdGenerator so intent is visible in each test.
 */
class MatchingEngineTest {

    private static final String SYMBOL = "TEST";

    private OrderBook book;
    private MatchingEngine engine;

    @BeforeEach
    void setUp() {
        book = new OrderBook(SYMBOL);
        engine = new MatchingEngine(book);
    }

    private Order gtc(long orderId, long seq, Side side, long price, long qty) {
        return new Order(orderId, seq, SYMBOL, /*userId*/ 1L, side, OrderType.GTC, price, qty);
    }

    // ------------------------------------------------------------------
    // Resting behavior (no match)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("bid below best ask rests untouched, no trades")
    void restsWithoutCrossing() {
        engine.processOrder(gtc(1, 100, Side.ASK, 100, 10)); // best ask = 100
        MatchResult result = engine.processOrder(gtc(2, 200, Side.BID, 99, 5));

        assertTrue(result.trades().isEmpty());
        assertTrue(result.restingOnBook());
        assertEquals(5, result.remainingQuantity());
        assertSame(book.findOrder(2), result.order());
        assertEquals(99, book.getBestBidPrice());
    }

    @Test
    @DisplayName("ask above best bid rests untouched, no trades")
    void askRestsWithoutCrossing() {
        engine.processOrder(gtc(1, 100, Side.BID, 100, 10)); // best bid = 100
        MatchResult result = engine.processOrder(gtc(2, 200, Side.ASK, 101, 5));

        assertTrue(result.trades().isEmpty());
        assertTrue(result.restingOnBook());
        assertEquals(101, book.getBestAskPrice());
    }

    @Test
    @DisplayName("equal prices cross: bid at 100 matches ask at 100")
    void equalPricesCross() {
        engine.processOrder(gtc(1, 100, Side.ASK, 100, 10));
        MatchResult result = engine.processOrder(gtc(2, 200, Side.BID, 100, 5));

        assertEquals(1, result.trades().size());
        assertEquals(100, result.trades().get(0).price()); // maker's price
        assertEquals(5, result.trades().get(0).quantity());
        assertFalse(result.restingOnBook());
        assertTrue(result.isFullyFilled());
        assertNull(book.findOrder(2), "fully filled taker must not rest");
    }

    // ------------------------------------------------------------------
    // Maker lifecycle: full fill, partial fill
    // ------------------------------------------------------------------

    @Test
    @DisplayName("taker larger than head maker: maker fully filled and leaves the book")
    void fullFillRemovesMaker() {
        engine.processOrder(gtc(1, 100, Side.ASK, 100, 10));
        MatchResult result = engine.processOrder(gtc(2, 200, Side.BID, 100, 25));

        assertEquals(1, result.trades().size());
        assertEquals(10, result.trades().get(0).quantity());
        assertNull(book.findOrder(1), "fully filled maker must leave the index");
        assertNull(book.getBestAskPrice(), "empty level must be removed from the map");
        assertTrue(result.restingOnBook());
        assertEquals(15, result.remainingQuantity());
        assertEquals(100, book.getBestBidPrice(), "taker remainder rests at its limit");
    }

    @Test
    @DisplayName("partial fill: maker keeps remaining qty and stays in the index")
    void partialFillKeepsMaker() {
        engine.processOrder(gtc(1, 100, Side.ASK, 100, 10));
        MatchResult result = engine.processOrder(gtc(2, 200, Side.BID, 100, 4));

        assertEquals(1, result.trades().size());
        Order maker = book.findOrder(1);
        assertNotNull(maker, "partially filled maker must remain on the book");
        assertEquals(6, maker.getRemainingQty());
        assertTrue(result.isFullyFilled());
        assertNull(book.findOrder(2));
    }

    @Test
    @DisplayName("partial fill does NOT cost the maker its FIFO priority")
    void partialFillKeepsPriority() {
        Order maker1 = gtc(1, 100, Side.ASK, 100, 10);
        Order maker2 = gtc(2, 200, Side.ASK, 100, 10);
        book.restOrder(maker1);
        book.restOrder(maker2);

        engine.processOrder(gtc(3, 300, Side.BID, 100, 5)); // partially fills maker1
        PriceLevel level = book.getBestAskLevel();
        assertSame(maker1, level.peekFirst(), "maker1 must still be at the head");

        MatchResult result = engine.processOrder(gtc(4, 400, Side.BID, 100, 15));
        // maker1 has 5 left, must fill completely before maker2 is touched
        assertEquals(2, result.trades().size());
        assertEquals(1, result.trades().get(0).makerOrderId());
        assertEquals(5, result.trades().get(0).quantity());
        assertEquals(2, result.trades().get(1).makerOrderId());
        assertEquals(10, result.trades().get(1).quantity());
        assertTrue(result.isFullyFilled());
        assertNull(book.findOrder(1));
        assertNull(book.findOrder(2));
    }

    // ------------------------------------------------------------------
    // Multi-level sweeps
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sweep across price levels executes at each maker's price")
    void sweepsMultipleLevelsAtMakerPrices() {
        engine.processOrder(gtc(1, 100, Side.ASK, 102, 5));
        engine.processOrder(gtc(2, 200, Side.ASK, 100, 5));
        engine.processOrder(gtc(3, 300, Side.ASK, 101, 5));

        MatchResult result = engine.processOrder(gtc(4, 400, Side.BID, 105, 12));

        List<Trade> trades = result.trades();
        assertEquals(3, trades.size());
        // asks visited best-first: 100, then 101, then 102
        assertEquals(100, trades.get(0).price());
        assertEquals(101, trades.get(1).price());
        assertEquals(102, trades.get(2).price());
        assertEquals(5, trades.get(0).quantity());
        assertEquals(5, trades.get(1).quantity());
        assertEquals(2, trades.get(2).quantity());

        assertFalse(result.restingOnBook());
        assertEquals(0, result.remainingQuantity());
        assertEquals(102, book.getBestAskPrice());
        assertNull(book.getBestBidPrice());

    }

    @Test
    @DisplayName("sweep stops at the first non-crossing level, remainder rests")
    void sweepStopsAtNonCrossingLevel() {
        engine.processOrder(gtc(1, 100, Side.ASK, 100, 5));
        engine.processOrder(gtc(2, 200, Side.ASK, 110, 5));

        MatchResult result = engine.processOrder(gtc(3, 300, Side.BID, 105, 10));

        assertEquals(1, result.trades().size());
        assertEquals(100, result.trades().get(0).price());
        assertEquals(5, result.trades().get(0).quantity());
        assertEquals(110, book.getBestAskPrice(), "non-crossing ask must survive");
        assertTrue(result.restingOnBook());
        assertEquals(5, result.remainingQuantity());
        assertEquals(105, book.getBestBidPrice());
    }

    @Test
    @DisplayName("bid side sweep: asks consume bids descending at maker prices")
    void askSideSweepsBids() {
        engine.processOrder(gtc(1, 100, Side.BID, 100, 5));
        engine.processOrder(gtc(2, 200, Side.BID, 102, 5));

        MatchResult result = engine.processOrder(gtc(3, 300, Side.ASK, 99, 12));

        List<Trade> trades = result.trades();
        assertEquals(2, trades.size());
        assertEquals(102, trades.get(0).price()); // best bid first
        assertEquals(100, trades.get(1).price());
        assertEquals(5, trades.get(0).quantity());
        assertEquals(5, trades.get(1).quantity());
        assertFalse(result.isFullyFilled(), "12 > 10, remainder rests");
        assertTrue(result.restingOnBook());
        assertEquals(2, result.remainingQuantity(), "12 - 5 - 5");
        assertEquals(99, book.getBestAskPrice(), "taker remainder rests below the swept bids");
    }

    // ------------------------------------------------------------------
    // Exact fills, trade contents, level totals
    // ------------------------------------------------------------------

    @Test
    @DisplayName("exact-size fill: no remainder, no resting, level cleaned up")
    void exactFill() {
        engine.processOrder(gtc(1, 100, Side.ASK, 100, 10));
        MatchResult result = engine.processOrder(gtc(2, 200, Side.BID, 100, 10));

        assertTrue(result.isFullyFilled());
        assertFalse(result.restingOnBook());
        assertEquals(10, result.filledQuantity());
        assertNull(book.findOrder(1));
        assertNull(book.findOrder(2));
        assertEquals(0, book.totalRestingOrders());
    }

    @Test
    @DisplayName("trade records carry maker/taker ids, users, symbol and ascending ids")
    void tradeContentsAreCorrect() {
        engine.processOrder(gtc(1, 100, Side.ASK, 100, 10));
        MatchResult result = engine.processOrder(gtc(2, 200, Side.BID, 100, 10));

        assertEquals(1, result.trades().size());
        Trade trade = result.trades().get(0);
        assertEquals(SYMBOL, trade.symbol());
        assertEquals(1, trade.makerOrderId());
        assertEquals(2, trade.takerOrderId());
        assertEquals(1, trade.makerUserId());
        assertEquals(1, trade.takerUserId());
        assertTrue(trade.tradeId() > 0);
        assertTrue(trade.sequence() > 0);
    }

    @Test
    @DisplayName("level totalQuantity tracks partial fills and removals")
    void levelTotalsStayConsistent() {
        Order maker = gtc(1, 100, Side.ASK, 100, 10);
        book.restOrder(maker);
        book.restOrder(gtc(2, 200, Side.ASK, 100, 7));

        PriceLevel level = book.getBestAskLevel();
        assertEquals(17, level.getTotalQuantity());

        engine.processOrder(gtc(3, 300, Side.BID, 100, 4)); // partial fill of maker
        assertEquals(13, level.getTotalQuantity());

        engine.processOrder(gtc(4, 400, Side.BID, 100, 13)); // sweeps both makers fully
        assertNull(book.getBestAskLevel(), "level must be gone once fully consumed");
    }

    @Test
    @DisplayName("empty opposite side: order rests immediately")
    void restsOnEmptyBook() {
        MatchResult result = engine.processOrder(gtc(1, 100, Side.BID, 100, 10));

        assertTrue(result.trades().isEmpty());
        assertTrue(result.restingOnBook());
        assertEquals(100, book.getBestBidPrice());
    }

    // ------------------------------------------------------------------
    // Unsupported types
    // ------------------------------------------------------------------

    @Test
    @DisplayName("non-GTC order types are rejected loudly")
    void nonGtcRejected() {
        for (OrderType type : List.of(OrderType.IOC, OrderType.FOK, OrderType.MARKET)) {
            Order order = new Order(1, 100, SYMBOL, 1L, Side.BID, type, 100, 10);
            assertThrows(UnsupportedOperationException.class,
                    () -> engine.processOrder(order), "type " + type + " must be rejected");
        }
    }
}
