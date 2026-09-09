package com.exchange.pip.core.events;

import com.exchange.pip.core.api.model.ClientOrder;
import com.exchange.pip.core.orderbook.EngineRegistry;
import com.lmax.disruptor.BusySpinWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;
import jakarta.inject.Singleton;
import java.util.concurrent.ThreadFactory;

@Singleton
public class EventDisruptor {

    private final ThreadFactory threadFactory = DaemonThreadFactory.INSTANCE;
    private final WaitStrategy waitStrategy = new BusySpinWaitStrategy();
    private final com.lmax.disruptor.dsl.Disruptor<OrderEvent> disruptor = new com.lmax.disruptor.dsl.Disruptor<OrderEvent>(OrderEvent.EVENT_FACTORY, 16, threadFactory, ProducerType.SINGLE, waitStrategy);
    private final RingBuffer<OrderEvent> ringBuffer;
    private final EngineRegistry engineRegistry;

    public EventDisruptor(EngineRegistry engineRegistry) {
        this.engineRegistry = engineRegistry;
        disruptor.handleEventsWith(engineRegistry.getEventHandlers());
        ringBuffer = disruptor.start();
    }

    public void publishEvent(ClientOrder order) {
        long sequenceId = ringBuffer.next();
        OrderEvent orderEvent = ringBuffer.get(sequenceId);
        orderEvent.setOrder(order);
        ringBuffer.publish(sequenceId);
    }

}
