package com.exchange.pip.core.orderbook;

import com.exchange.pip.core.orderbook.*;
import com.exchange.pip.core.shared.IdGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private final IdGenerator ids = new IdGenerator();

    private Order order(long id, Side side, long price, long qty) {
        return new Order(id, ids.next(), "BTC-USD", 100L, side, OrderType.GTC, price, qty);
    }

    @Test
    void emptyBookHasNoBestPrices() {
        OrderBook book = new OrderBook("BTC-USD");
        assertNull(book.getBestBidPrice());
        assertNull(book.getBestAskPrice());
    }

    @Test
    void bestBidIsHighestPrice() {
        OrderBook book = new OrderBook("BTC-USD");
        book.restOrder(order(1, Side.BID, 100L, 10L));
        book.restOrder(order(2, Side.BID, 105L, 5L));
        book.restOrder(order(3, Side.BID, 95L, 3L));

        assertEquals(105L, book.getBestBidPrice());
    }

    @Test
    void bestAskIsLowestPrice() {
        OrderBook book = new OrderBook("BTC-USD");
        book.restOrder(order(1, Side.ASK, 110L, 10L));
        book.restOrder(order(2, Side.ASK, 108L, 5L));
        book.restOrder(order(3, Side.ASK, 115L, 3L));

        assertEquals(108L, book.getBestAskPrice());
    }

    @Test
    void ordersAtSamePriceShareOneLevel() {
        OrderBook book = new OrderBook("BTC-USD");
        book.restOrder(order(1, Side.BID, 100L, 10L));
        book.restOrder(order(2, Side.BID, 100L, 5L));

        PriceLevel level = book.getBestBidLevel();
        assertEquals(100L, level.getPrice());
        assertEquals(2, level.size());
        assertEquals(15L, level.getTotalQuantity());
    }

    @Test
    void findOrderReturnsRestingOrder() {
        OrderBook book = new OrderBook("BTC-USD");
        Order o = order(1, Side.BID, 100L, 10L);
        book.restOrder(o);

        assertEquals(o, book.findOrder(1L));
        assertNull(book.findOrder(999L));
    }

    @Test
    void restOrderRejectsDuplicateOrderId() {
        OrderBook book = new OrderBook("BTC-USD");
        book.restOrder(order(1, Side.BID, 100L, 10L));

        assertThrows(IllegalStateException.class,
            () -> book.restOrder(order(1, Side.BID, 101L, 5L)));
    }

    @Test
    void removeOrderCleansUpEmptyLevel() {
        OrderBook book = new OrderBook("BTC-USD");
        book.restOrder(order(1, Side.BID, 100L, 10L));

        assertEquals(100L, book.getBestBidPrice());
        assertTrue(book.removeOrder(1L));
        assertNull(book.getBestBidPrice()); // level removed entirely
        assertNull(book.findOrder(1L));
    }

    @Test
    void removeOrderKeepsLevelIfOthersRemain() {
        OrderBook book = new OrderBook("BTC-USD");
        book.restOrder(order(1, Side.BID, 100L, 10L));
        book.restOrder(order(2, Side.BID, 100L, 5L));

        book.removeOrder(1L);
        assertEquals(100L, book.getBestBidPrice()); // level still there
        assertEquals(1, book.getBestBidLevel().size());
    }

    @Test
    void removeOrderReturnsFalseForUnknownId() {
        OrderBook book = new OrderBook("BTC-USD");
        assertFalse(book.removeOrder(12345L));
    }

    @Test
    void onHeadOrderFullyFilledUpdatesBookkeeping() {
        OrderBook book = new OrderBook("BTC-USD");
        Order o = order(1, Side.BID, 100L, 10L);
        book.restOrder(o);

        // simulate match loop: order fully filled, popped from its level directly
        PriceLevel level = book.getBestBidLevel();
        level.removeFirst();
        book.onHeadOrderFullyFilled(o);

        assertNull(book.getBestBidPrice());
        assertNull(book.findOrder(1L));
    }

    @Test
    void totalRestingOrdersTracksCorrectly() {
        OrderBook book = new OrderBook("BTC-USD");
        assertEquals(0, book.totalRestingOrders());

        book.restOrder(order(1, Side.BID, 100L, 10L));
        book.restOrder(order(2, Side.ASK, 110L, 10L));
        assertEquals(2, book.totalRestingOrders());

        book.removeOrder(1L);
        assertEquals(1, book.totalRestingOrders());
    }

    @Test
    void bidsAndAsksAreIndependentSides() {
        OrderBook book = new OrderBook("BTC-USD");
        book.restOrder(order(1, Side.BID, 100L, 10L));
        book.restOrder(order(2, Side.ASK, 100L, 5L)); // same price, opposite side

        assertEquals(100L, book.getBestBidPrice());
        assertEquals(100L, book.getBestAskPrice());
        assertEquals(2, book.totalRestingOrders());
    }
}