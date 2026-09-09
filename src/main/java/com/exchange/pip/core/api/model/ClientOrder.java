package com.exchange.pip.core.api.model;

import com.exchange.pip.core.orderbook.OrderType;
import com.exchange.pip.core.orderbook.Side;

/**
 * Represents an order submitted by a client.
 *
 * This object contains only client/business-level information.
 * It has no matching-engine sequence or mutable matching state.
 */
public record ClientOrder(
        long orderId,
        long userId,
        String symbol,
        Side side,
        OrderType orderType,
        long price,
        long quantity
) {

    public ClientOrder withOrderId(long newOrderId) {
        return new ClientOrder(newOrderId, userId, symbol, side, orderType, price, quantity);
    }

    public ClientOrder {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        if (orderType != OrderType.MARKET && price <= 0) {
            throw new IllegalArgumentException(
                    "price must be positive for non-market orders"
            );
        }
    }
}