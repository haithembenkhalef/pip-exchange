package com.exchange.pip.core.orderbook;

import com.exchange.pip.core.api.model.ClientOrder;
import com.exchange.pip.core.orderbook.events.OrderEvent;
import com.lmax.disruptor.EventHandler;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApplicationScoped
public final class MainEngineRouter implements EventHandler<OrderEvent> {

    private static final Logger logger = LoggerFactory.getLogger(MainEngineRouter.class);

    private final EngineRegistry engineRegistry;
    private final List<MatchResult> results = new ArrayList<>();

    public MainEngineRouter(EngineRegistry engineRegistry) {
        this.engineRegistry = engineRegistry;
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) throws IOException {
        //logger.debug("Id is {} sequence id that was used is {}", event.getOrder().orderId(), sequence);
        ClientOrder order = event.getOrder();
        if (order == null)
            return;
        MatchingEngine matchingEngine = engineRegistry.get(order.symbol());
        MatchResult matchResult = matchingEngine.handleOrderEvent(event);
        results.add(matchResult);
        //TODO: Make the snapshot every x time/order
        matchingEngine.snapshotBook(event.getSequence());
    }

    public List<MatchResult> getResults() {
        return Collections.unmodifiableList(results);
    }

    public void clear() {
        results.clear();
    }
}
