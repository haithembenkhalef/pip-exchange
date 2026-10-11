package com.exchange.pip.core.orderbook;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Deque;
import java.util.Map;
import java.util.NavigableMap;

final class OrderBookSnapshotCodec {

    private static final int MAGIC = 0x4F424B31;      // "OBK1"
    private static final int RECORD_SIZE = 6 * 8 + 2; // 6 longs + side + type
    private static final int BUF_SIZE = 1 << 20;

    private static final Side[] SIDES = Side.values();
    private static final OrderType[] TYPES = OrderType.values();

    // ---- VarHandles into your private fields (resolved once) ----
    private static final VarHandle BOOK_SYMBOL, BOOK_BIDS, BOOK_ASKS, BOOK_BY_ID;
    private static final VarHandle LVL_ORDERS, LVL_TOTAL;
    private static final VarHandle O_ID, O_SEQ, O_USER, O_SIDE, O_TYPE, O_PRICE, O_ORIG, O_REM;

    static {
        try {
            var b = MethodHandles.privateLookupIn(OrderBook.class, MethodHandles.lookup());
            BOOK_SYMBOL = b.findVarHandle(OrderBook.class, "symbol", String.class);
            BOOK_BIDS = b.findVarHandle(OrderBook.class, "bids", NavigableMap.class);
            BOOK_ASKS = b.findVarHandle(OrderBook.class, "asks", NavigableMap.class);
            BOOK_BY_ID = b.findVarHandle(OrderBook.class, "ordersById", Map.class);

            var l = MethodHandles.privateLookupIn(PriceLevel.class, MethodHandles.lookup());
            LVL_ORDERS = l.findVarHandle(PriceLevel.class, "orders", Deque.class);
            LVL_TOTAL = l.findVarHandle(PriceLevel.class, "totalQuantity", long.class);

            var o = MethodHandles.privateLookupIn(Order.class, MethodHandles.lookup());
            O_ID = o.findVarHandle(Order.class, "orderId", long.class);
            O_SEQ = o.findVarHandle(Order.class, "sequence", long.class);
            O_USER = o.findVarHandle(Order.class, "userId", long.class);
            O_SIDE = o.findVarHandle(Order.class, "side", Side.class);
            O_TYPE = o.findVarHandle(Order.class, "orderType", OrderType.class);
            O_PRICE = o.findVarHandle(Order.class, "price", long.class);
            O_ORIG = o.findVarHandle(Order.class, "originalQty", long.class);
            O_REM = o.findVarHandle(Order.class, "remainingQty", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private OrderBookSnapshotCodec() {
    }

    // ======================= SAVE =======================
    @SuppressWarnings("unchecked")
    static void write(OrderBook book, Path file) throws IOException {
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");

        var bids = (NavigableMap<Long, PriceLevel>) BOOK_BIDS.get(book);
        var asks = (NavigableMap<Long, PriceLevel>) BOOK_ASKS.get(book);
        var byId = (Map<Long, Order>) BOOK_BY_ID.get(book);
        String symbol = (String) BOOK_SYMBOL.get(book);

        try (FileChannel ch = FileChannel.open(tmp, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {

            ByteBuffer buf = ByteBuffer.allocateDirect(BUF_SIZE);
            byte[] sym = symbol.getBytes(StandardCharsets.UTF_8);

            buf.putInt(MAGIC);
            buf.putShort((short) sym.length);
            buf.put(sym);
            buf.putInt(byId.size());

            writeSide(ch, buf, bids);
            writeSide(ch, buf, asks);

            flush(ch, buf);
            ch.force(false);
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
    }

    @SuppressWarnings("unchecked")
    private static void writeSide(FileChannel ch, ByteBuffer buf,
                                  NavigableMap<Long, PriceLevel> side) throws IOException {
        for (PriceLevel lvl : side.values()) {
            for (Order o : (Deque<Order>) LVL_ORDERS.get(lvl)) {   // FIFO order preserved
                if (buf.remaining() < RECORD_SIZE) flush(ch, buf);
                buf.putLong((long) O_ID.get(o));
                buf.putLong((long) O_SEQ.get(o));
                buf.putLong((long) O_USER.get(o));
                buf.putLong((long) O_PRICE.get(o));
                buf.putLong((long) O_ORIG.get(o));
                buf.putLong((long) O_REM.get(o));
                buf.put((byte) ((Side) O_SIDE.get(o)).ordinal());
                buf.put((byte) ((OrderType) O_TYPE.get(o)).ordinal());
            }
        }
    }

    private static void flush(FileChannel ch, ByteBuffer buf) throws IOException {
        buf.flip();
        while (buf.hasRemaining()) ch.write(buf);
        buf.clear();
    }

    // ======================= RESTORE =======================
    @SuppressWarnings("unchecked")
    static OrderBook load(Path file) throws IOException {
        try (FileChannel ch = FileChannel.open(file, StandardOpenOption.READ)) {
            MappedByteBuffer buf = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size());

            if (buf.getInt() != MAGIC) throw new IOException("Not an order book snapshot");

            byte[] sym = new byte[buf.getShort()];
            buf.get(sym);
            String symbol = new String(sym, StandardCharsets.UTF_8);
            int count = buf.getInt();

            OrderBook book = new OrderBook(symbol);
            var bids = (NavigableMap<Long, PriceLevel>) BOOK_BIDS.get(book);
            var asks = (NavigableMap<Long, PriceLevel>) BOOK_ASKS.get(book);
            var byId = (Map<Long, Order>) BOOK_BY_ID.get(book);

            // Orders are stored level by level, so cache the current level
            // and skip the TreeMap lookup for consecutive orders.
            PriceLevel cur = null;
            Deque<Order> curQueue = null;
            long curPrice = -1;
            Side curSide = null;
            long curTotal = 0;

            for (int i = 0; i < count; i++) {
                long orderId = buf.getLong();
                long seq = buf.getLong();
                long userId = buf.getLong();
                long price = buf.getLong();
                long origQty = buf.getLong();
                long remQty = buf.getLong();
                Side side = SIDES[buf.get()];
                OrderType t = TYPES[buf.get()];

                Order o = new Order(orderId, seq, symbol, userId, side, t, price, origQty);
                O_REM.set(o, remQty);

                if (cur == null || price != curPrice || side != curSide) {
                    if (cur != null) LVL_TOTAL.set(cur, curTotal);
                    var map = (side == Side.BID) ? bids : asks;
                    cur = map.computeIfAbsent(price, PriceLevel::new);
                    curQueue = (Deque<Order>) LVL_ORDERS.get(cur);
                    curTotal = (long) LVL_TOTAL.get(cur);
                    curPrice = price;
                    curSide = side;
                }
                curQueue.addLast(o);
                curTotal += remQty;
                byId.put(orderId, o);
            }
            if (cur != null) LVL_TOTAL.set(cur, curTotal);
            return book;
        }
    }

}