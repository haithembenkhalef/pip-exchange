package com.exchange.pip.core.orderbook;

import com.exchange.pip.core.orderbook.Order;
import com.exchange.pip.core.orderbook.OrderType;
import com.exchange.pip.core.orderbook.Side;
import com.exchange.pip.core.shared.IdGenerator;
import com.exchange.pip.core.trade.Trade;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private final IdGenerator ids = new IdGenerator();

    @Test
    void newOrderStartsFullyUnfilled() {
        Order o = new Order(1L, ids.next(), "BTC-USD", 100L,
            Side.BID, OrderType.GTC, 50_000_00L, 10L);

        assertEquals(10L, o.getRemainingQty());
        assertFalse(o.isFullyFilled());
    }

    @Test
    void reduceRemainingDecrementsCorrectly() {
        Order o = new Order(1L, ids.next(), "BTC-USD", 100L,
            Side.ASK, OrderType.GTC, 50_000_00L, 10L);

        o.reduceRemaining(4L);
        assertEquals(6L, o.getRemainingQty());

        o.reduceRemaining(6L);
        assertEquals(0L, o.getRemainingQty());
        assertTrue(o.isFullyFilled());
    }

    @Test
    void reduceRemainingRejectsOverfill() {
        Order o = new Order(1L, ids.next(), "BTC-USD", 100L,
            Side.BID, OrderType.GTC, 50_000_00L, 5L);

        assertThrows(IllegalArgumentException.class, () -> o.reduceRemaining(6L));
    }

    @Test
    void marketOrderAllowsZeroPrice() {
        // price is irrelevant for MARKET orders — should not throw
        assertDoesNotThrow(() ->
            new Order(1L, ids.next(), "BTC-USD", 100L, Side.BID, OrderType.MARKET, 0L, 3L));
    }

    @Test
    void limitOrderRejectsNonPositivePrice() {
        assertThrows(IllegalArgumentException.class, () ->
            new Order(1L, ids.next(), "BTC-USD", 100L, Side.BID, OrderType.GTC, 0L, 3L));
    }

    @Test
    void tradeRejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () ->
            new Trade(1L, "BTC-USD", 50_000_00L, 0L, 10L, 100L, 11L, 200L, 1L));
    }
}