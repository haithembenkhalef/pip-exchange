package com.exchange.pip.core.orderbook.events;

import com.exchange.pip.core.api.model.ClientOrder;
import com.lmax.disruptor.EventFactory;

public final class OrderEvent {
    private ClientOrder order;
    public final static EventFactory EVENT_FACTORY = OrderEvent::new;

    private long sequence = -1; // unset until the sequencer handler stamps it

    public ClientOrder getOrder() { return order; }
    public void setOrder(ClientOrder order) { this.order = order; }

    public long getSequence() { return sequence; }
    public void setSequence(long sequence) { this.sequence = sequence; }
}
