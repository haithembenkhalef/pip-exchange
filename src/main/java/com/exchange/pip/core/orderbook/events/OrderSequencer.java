package com.exchange.pip.core.orderbook.events;

import com.exchange.pip.core.shared.IdGenerator;
import com.lmax.disruptor.EventHandler;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public final class OrderSequencer implements EventHandler<OrderEvent> {

    private final IdGenerator sequenceGenerator;

    public OrderSequencer() {
        this.sequenceGenerator = new IdGenerator();
    }

    @Override
    public void onEvent(OrderEvent event, long ringBufferSequence, boolean endOfBatch) {
        event.setSequence(sequenceGenerator.next());
    }
}