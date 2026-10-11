package com.exchange.pip.core.orderbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static com.exchange.pip.core.orderbook.OrderBookSnapshot.snapshotDir;
import static org.junit.jupiter.api.Assertions.*;

class OrderBookSnapshotTest {

    private static final String SYMBOL = "TEST";

    private OrderBook book;
    private MatchingEngine engine;
    private OrderBookSnapshot orderBookSnapshoter;

    @BeforeEach
    void setUp() {
        book = new OrderBook(SYMBOL);
        engine = new MatchingEngine(book);
        orderBookSnapshoter = new OrderBookSnapshot();
    }

    private Order gtc(long orderId, long seq, Side side, long price, long qty) {
        return new Order(orderId, seq, SYMBOL, 1L, side, OrderType.GTC, price, qty);
    }

    @Test
    void snapshotFileAndContentAreCorrect() throws IOException {
        // two bids at 100 (FIFO), one ask at 101 that is partially filled (6 -> 4)
        engine.processOrder(gtc(1, 100, Side.BID, 100, 10));
        engine.processOrder(gtc(2, 200, Side.BID, 100, 7));
        engine.processOrder(gtc(3, 300, Side.ASK, 101, 6));
        engine.processOrder(gtc(4, 400, Side.BID, 101, 2));

        long lastSeq = 400;
        Path file = OrderBookSnapshot.pathFor(snapshotDir, book.getSymbol(), lastSeq);
        orderBookSnapshoter.snapshotBook(book, lastSeq);

        // ---- the file ----
        assertTrue(Files.exists(file), "snapshot file must exist");
        assertEquals("TEST-00000000000000000400.snap", file.getFileName().toString());
        assertTrue(Files.size(file) > 0);
        try (var s = Files.list(snapshotDir)) {
            List<String> names = s.map(p -> p.getFileName().toString()).toList();
            assertEquals(List.of(file.getFileName().toString()), names, "no .tmp left behind");
        }

        // ---- the content ----
        var restored = orderBookSnapshoter.restoreBookFromSnapshot(SYMBOL);

        assertEquals(SYMBOL, restored.getSymbol());
        assertEquals(3, restored.totalRestingOrders());
        assertEquals(100, restored.getBestBidPrice());
        assertEquals(101, restored.getBestAskPrice());

        assertEquals(10, restored.findOrder(1).getRemainingQty());
        assertEquals(7, restored.findOrder(2).getRemainingQty());
        assertEquals(4, restored.findOrder(3).getRemainingQty(), "partial fill preserved");
        assertNull(restored.findOrder(4), "fully filled taker must not be restored");

        assertEquals(17, restored.getBestBidLevel().getTotalQuantity(), "bid level total rebuilt");
        assertEquals(4, restored.getBestAskLevel().getTotalQuantity(), "ask level total rebuilt");
        assertSame(restored.findOrder(1), restored.getBestBidLevel().peekFirst(), "FIFO head is order 1");
    }
}