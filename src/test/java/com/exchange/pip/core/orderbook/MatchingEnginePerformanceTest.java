package com.exchange.pip.core.orderbook;

import com.exchange.pip.core.api.model.ClientOrder;
import com.exchange.pip.core.orderbook.events.EventDisruptor;
import com.exchange.pip.core.orderbook.events.MainEngineRouter;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

@QuarkusTest
class MatchingEnginePerformanceTest {

    private static final int ORDER_COUNT = 1_000_000;

    @Inject
    EngineRegistry engineRegistry;

    @Inject
    MainEngineRouter router;

    @Inject
    EventDisruptor eventDisruptor;

    @Test
    void benchmarkOrdersColdStart() throws InterruptedException {
        // Generate orders BEFORE timing
        List<ClientOrder> orders = generateClientOrders();

        long start = System.nanoTime();
        for (int i = 0; i < ORDER_COUNT; i++) {
            eventDisruptor.publishEvent(orders.get(i % ORDER_COUNT));
        }

        // Wait until every order has been processed
        List<MatchResult> results = router.getResults();
        while (results.size() < ORDER_COUNT) {
            Thread.sleep(1);
        }

        long end = System.nanoTime();

        // 2. CALCULATE LATENCIES FROM RESULTS
        long totalLatencyNanos = 0;
        long maxLatencyNanos = 0;

        // Use a traditional loop or stream to compute averages
        for (MatchResult res : results) {
            long lat = res.latency();
            totalLatencyNanos += lat;
            if (lat > maxLatencyNanos) {
                maxLatencyNanos = lat;
            }
        }

        double avgLatencyMicros = (totalLatencyNanos / (double) ORDER_COUNT) / 1_000.0;
        double maxLatencyMillis = maxLatencyNanos / 1_000_000.0;

        long elapsedNanos = end - start;
        double elapsedMillis = elapsedNanos / 1_000_000.0;
        double ordersPerSecond = ORDER_COUNT / (elapsedNanos / 1_000_000_000.0);

        System.out.println();
        System.out.println("========== Disruptor Benchmark ==========");
        System.out.println("Orders Processed: " + results.size());
        System.out.println("Total Time:       " + elapsedMillis + " ms");
        System.out.println("Throughput:       " + String.format("%,.2f", ordersPerSecond) + " orders/sec");
        System.out.println("------------------------------------------");
        System.out.println("Avg Latency:      " + String.format("%.3f", avgLatencyMicros) + " microseconds (µs)");
        System.out.println("Max Latency:      " + String.format("%.3f", maxLatencyMillis) + " ms");
        System.out.println("==========================================");
    }

    @Test
    void benchmarkOrdersHotStart() throws InterruptedException {
        // Generate orders BEFORE timing
        MatchingEngine matchingEngine = engineRegistry.get("AAPL");
        matchingEngine.clearBook();
        List<MatchResult> results = router.getResults();
        router.clear();
        List<ClientOrder> orders = generateClientOrders();

        long start = System.nanoTime();
        for (int i = 0; i < ORDER_COUNT; i++) {
            eventDisruptor.publishEvent(orders.get(i % ORDER_COUNT));
        }

        // Wait until every order has been processed
        while (results.size() < ORDER_COUNT) {
            Thread.sleep(1);
        }

        long end = System.nanoTime();

        // 2. CALCULATE LATENCIES FROM RESULTS
        long totalLatencyNanos = 0;
        long maxLatencyNanos = 0;

        // Use a traditional loop or stream to compute averages
        MatchResult maxLatencyResult = null;
        for (MatchResult res : results) {
            long lat = res.latency();
            totalLatencyNanos += lat;
            if (lat > maxLatencyNanos) {
                maxLatencyNanos = lat;
                maxLatencyResult = res;
            }
        }

        double avgLatencyMicros = (totalLatencyNanos / (double) ORDER_COUNT) / 1_000.0;
        double maxLatencyMillis = maxLatencyNanos / 1_000_000.0;

        long elapsedNanos = end - start;
        double elapsedMillis = elapsedNanos / 1_000_000.0;
        double ordersPerSecond = ORDER_COUNT / (elapsedNanos / 1_000_000_000.0);

        System.out.println();
        System.out.println("========== Disruptor Benchmark ==========");
        System.out.println("Orders Processed: " + results.size());
        System.out.println("Total Time:       " + elapsedMillis + " ms");
        System.out.println("Throughput:       " + String.format("%,.2f", ordersPerSecond) + " orders/sec");
        System.out.println("------------------------------------------");
        System.out.println("Avg Latency:      " + String.format("%.3f", avgLatencyMicros) + " microseconds (µs)");
        System.out.println("Max Latency:      " + String.format("%.3f", maxLatencyMillis) + " ms");
        System.out.println("Max Latency Result Details:      " + maxLatencyResult);
        System.out.println("==========================================");
    }

    private List<Order> generateOrders() {

        List<Order> orders = new ArrayList<>(ORDER_COUNT);

        for (int i = 0; i < ORDER_COUNT; i++) {

            Side side = (i % 2 == 0)
                    ? Side.BID
                    : Side.ASK;

            long price = 18_500 + (i % 201);

            long quantity = 1 + (i % 100);

            orders.add(new Order(
                    i + 1,             // orderId
                    i + 1,             // sequence
                    "AAPL",
                    100_000L + i,     // userId
                    side,
                    OrderType.GTC,
                    price,
                    quantity
            ));
        }

        return orders;
    }

    private List<ClientOrder> generateClientOrders() {

        List<ClientOrder> orders = new ArrayList<>(ORDER_COUNT);

        for (int i = 0; i < ORDER_COUNT; i++) {

            Side side = (i % 2 == 0)
                    ? Side.BID
                    : Side.ASK;

            long price = 18_500 + (i % 201);
            long quantity = 1 + (i % 100);

            orders.add(new ClientOrder(
                    i + 1,
                    100_000L + i,
                    "AAPL",
                    side,
                    OrderType.GTC,
                    price,
                    quantity
            ));
        }

        return orders;
    }
}