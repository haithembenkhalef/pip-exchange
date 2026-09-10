package com.exchange.pip.core.orderbook;

import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One MatchingEngine (and therefore one OrderBook) per symbol.
 *
 * This is the multi-symbol equivalent of the single-book setup from
 * before — each symbol gets its own independent book/engine pair,
 * mirroring exchange-core's own sharding-by-symbol design. Different
 * symbols never share state, so nothing here needs locking even once
 * you eventually run different symbols' engines on different threads.
 *
 * Framework-agnostic on purpose — plain domain code, no CDI here.
 * EngineProducer is what populates and exposes this to the app.
 */

@Singleton
public final class EngineRegistry {

    private final Map<String, MatchingEngine> enginesBySymbol;

    public EngineRegistry() {
        enginesBySymbol = new ConcurrentHashMap<>(
                Map.of("AAPL", new MatchingEngine(new OrderBook("AAPL")))
        );
    }

    public EngineRegistry(Map<String, MatchingEngine> enginesBySymbol) {
        this.enginesBySymbol = Map.copyOf(enginesBySymbol);
    }

    /**
     * @throws UnknownSymbolException if no engine exists for this symbol —
     *         callers (the event consumer, the API layer) must handle this
     *         as a rejection, not let it propagate as a raw NPE/lookup miss.
     */
    public MatchingEngine get(String symbol) {
        MatchingEngine engine = enginesBySymbol.get(symbol);
        if (engine == null) {
            throw new UnknownSymbolException(symbol);
        }
        return engine;
    }

    public boolean supports(String symbol) {
        return enginesBySymbol.containsKey(symbol);
    }

    public static final class UnknownSymbolException extends RuntimeException {
        public UnknownSymbolException(String symbol) {
            super("no matching engine for symbol: " + symbol);
        }
    }
}