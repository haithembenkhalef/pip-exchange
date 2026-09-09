package com.exchange.pip.core.events;

import com.exchange.pip.core.api.model.ClientOrder;
import com.exchange.pip.core.orderbook.EngineRegistry;
import com.exchange.pip.core.orderbook.MatchingEngine;
import com.lmax.disruptor.EventHandler;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public final class MainEngineRouter implements EventHandler<OrderEvent> {

    private static final Logger logger = LoggerFactory.getLogger(MainEngineRouter.class);

    private final EngineRegistry engineRegistry;

    public MainEngineRouter(EngineRegistry engineRegistry) {
        this.engineRegistry = engineRegistry;
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        //logger.debug("Id is {} sequence id that was used is {}", event.getOrder().orderId(), sequence);
        ClientOrder order = event.getOrder();
        if (order == null)
            return;
        MatchingEngine matchingEngine = engineRegistry.get(order.symbol());
        matchingEngine.handleOrderEvent(order);
    }
}
