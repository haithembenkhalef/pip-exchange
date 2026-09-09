package com.exchange.pip.core.api.web;

import com.exchange.pip.core.api.model.ClientOrder;
import com.exchange.pip.core.orderbook.events.EventDisruptor;
import com.exchange.pip.core.shared.OrderIdGenerator;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.jboss.resteasy.reactive.RestResponse;

@Path("/v1/order")
public class OrderApi {

    @Inject
    EventDisruptor dispatcher;

    @Inject
    OrderIdGenerator idGenerator;

    @POST
    public RestResponse<ClientOrder> placeOrder(ClientOrder order) {
        long orderId = idGenerator.next();
        ClientOrder clientOrder = order.withOrderId(orderId);
        dispatcher.publishEvent(clientOrder);
        return RestResponse.ok(clientOrder);
    }
}
