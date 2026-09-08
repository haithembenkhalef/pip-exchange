package com.exchange.pip.core.orderbook;

import com.exchange.pip.core.orderbook.Order;
import com.exchange.pip.core.orderbook.OrderType;
import com.exchange.pip.core.orderbook.PriceLevel;
import com.exchange.pip.core.orderbook.Side;
import com.exchange.pip.core.shared.IdGenerator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PriceLevelTest {

    private final IdGenerator ids = new IdGenerator();

    private Order order(long id, long price, long qty) {
        return new Order(id, ids.next(), "BTC-USD", 100L, Side.BID, OrderType.GTC, price, qty);
    }

    @Test
    void startsEmpty() {
        PriceLevel level = new PriceLevel(100L);
        assertTrue(level.isEmpty());
        assertEquals(0, level.size());
        assertEquals(0L, level.getTotalQuantity());
        assertNull(level.peekFirst());
    }

    @Test
    void addAccumulatesTotalQuantity() {
        PriceLevel level = new PriceLevel(100L);
        level.add(order(1, 100L, 10L));
        level.add(order(2, 100L, 5L));

        assertEquals(15L, level.getTotalQuantity());
        assertEquals(2, level.size());
    }

    @Test
    void rejectsOrderWithMismatchedPrice() {
        PriceLevel level = new PriceLevel(100L);
        assertThrows(IllegalArgumentException.class, () -> level.add(order(1, 999L, 10L)));
    }

    @Test
    void fifoOrderIsPreserved() {
        PriceLevel level = new PriceLevel(100L);
        Order first = order(1, 100L, 10L);
        Order second = order(2, 100L, 5L);
        level.add(first);
        level.add(second);

        assertEquals(first, level.peekFirst());
        level.removeFirst();
        assertEquals(second, level.peekFirst());
    }

    @Test
    void removeFirstUpdatesTotalQuantity() {
        PriceLevel level = new PriceLevel(100L);
        level.add(order(1, 100L, 10L));
        level.add(order(2, 100L, 5L));

        level.removeFirst();
        assertEquals(5L, level.getTotalQuantity());
        assertEquals(1, level.size());
    }

    @Test
    void removeSpecificOrderFromMiddle() {
        PriceLevel level = new PriceLevel(100L);
        Order a = order(1, 100L, 10L);
        Order b = order(2, 100L, 5L);
        Order c = order(3, 100L, 7L);
        level.add(a);
        level.add(b);
        level.add(c);

        assertTrue(level.remove(b));
        assertEquals(17L, level.getTotalQuantity()); // 10 + 7
        assertEquals(a, level.peekFirst());
        level.removeFirst();
        assertEquals(c, level.peekFirst()); // b was skipped, order preserved
    }

    @Test
    void removeNonExistentOrderReturnsFalse() {
        PriceLevel level = new PriceLevel(100L);
        level.add(order(1, 100L, 10L));
        Order notInLevel = order(999, 100L, 1L);

        assertFalse(level.remove(notInLevel));
        assertEquals(10L, level.getTotalQuantity()); // unchanged
    }

    @Test
    void addRejectsOutOfSequenceOrder() {
        PriceLevel level = new PriceLevel(100L);
        Order first = order(1, 100L, 10L);   // lower sequence (created first)
        Order second = order(2, 100L, 5L);   // higher sequence (created second)

        level.add(second);
        // second is already queued with the higher sequence; adding "first" now
        // would insert an order with a LOWER sequence behind a higher one —
        // that's a violation of arrival order and must be rejected
        assertThrows(IllegalStateException.class, () -> level.add(first));
    }

    @Test
    void onPartialFillReducesTotalWithoutRemovingOrder() {
        PriceLevel level = new PriceLevel(100L);
        Order a = order(1, 100L, 10L);
        level.add(a);

        a.reduceRemaining(4L); // simulate matching engine applying a fill
        level.onPartialFill(4L);

        assertEquals(6L, level.getTotalQuantity());
        assertEquals(a, level.peekFirst()); // still at the head, kept priority
        assertEquals(1, level.size());
    }
}