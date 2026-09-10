package com.exchange.pip.core.orderbook.command;

import com.exchange.pip.core.api.model.ClientOrder;
import com.exchange.pip.core.orderbook.OrderType;
import com.exchange.pip.core.orderbook.Side;

import java.nio.ByteBuffer;

public final class OrderCommandCodec {

    public static final int RECORD_SIZE = 48; // Fixed binary stride length

    private OrderCommandCodec() {
    }

    /**
     * Encodes a ClientOrder directly into a ByteBuffer (Zero Allocation).
     */
    public static void encode(ByteBuffer buf, long sequence, ClientOrder order, long symbolId) {
        buf.putLong(sequence);
        buf.putLong(order.orderId());
        buf.putLong(order.userId());
        buf.putLong(order.price());
        buf.putLong(order.quantity());
        buf.putLong(symbolId);
        buf.put(encodeSide(order.side()));
        buf.put(encodeType(order.orderType()));
        buf.putInt(0); // 4-byte padding for 64-bit memory alignment
    }

    /**
     * Decodes binary fields from the buffer into a reusable container (Zero Allocation).
     */
    public static void decode(ByteBuffer buf, int offset, DecodedOrderCommand target) {
        target.sequence = buf.getLong(offset);
        target.orderId = buf.getLong(offset + 8);
        target.userId = buf.getLong(offset + 16);
        target.price = buf.getLong(offset + 24);
        target.quantity = buf.getLong(offset + 32);
        target.symbolId = buf.getLong(offset + 40);
        target.side = decodeSide(buf.get(offset + 42));
        target.orderType = decodeType(buf.get(offset + 43));
    }

    private static byte encodeSide(Side side) {
        return side == Side.ASK ? (byte) 1 : (byte) 2;
    }

    private static Side decodeSide(byte side) {
        return side == 1 ? Side.ASK : Side.BID;
    }

    private static byte encodeType(OrderType type) {
        return switch (type) {
            case GTC -> (byte) 1;
            case IOC -> (byte) 2;
            case FOK -> (byte) 3;
            case MARKET -> (byte) 4;

        };
    }

    private static OrderType decodeType(byte type) {
        return switch (type) {
            case 1 -> OrderType.GTC;
            case 2 -> OrderType.IOC;
            case 3 -> OrderType.FOK;
            case 4 -> OrderType.MARKET;
            default -> throw new IllegalArgumentException("Unknown order type byte: " + type);
        };
    }

    /**
     * Reusable container object populated during recovery replay.
     */
    public static final class DecodedOrderCommand {
        public long sequence;
        public long orderId;
        public long userId;
        public long price;
        public long quantity;
        public long symbolId;
        public Side side;
        public OrderType orderType;
    }
}