package com.exchange.pip.core.events;

import com.exchange.pip.core.api.model.ClientOrder;
import com.lmax.disruptor.EventFactory;

public final class OrderEvent {
    private ClientOrder order;
    public final static EventFactory EVENT_FACTORY
      = OrderEvent::new;

    public ClientOrder getOrder() {
        return order;
    }

    public void setOrder(ClientOrder order) {
        this.order = order;
    }
}
