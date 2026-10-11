package com.exchange.pip.core.orderbook;

import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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

    private final Map<String, MatchingEngine> enginesBySymbol = new ConcurrentHashMap();

    private static final List<String> SYMBOLS = List.of("AAPL");

    public EngineRegistry() {
        OrderBookSnapshot orderBookSnapshot = new OrderBookSnapshot();
        for (String symbol : SYMBOLS) {
            OrderBook loaded;
            try {
                loaded = orderBookSnapshot.restoreBookFromSnapshot(symbol);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            OrderBook book = loaded != null ? loaded : new OrderBook(symbol);
            enginesBySymbol.put(symbol, new MatchingEngine(book));
        }
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

    public List<String> listSupportedSymbols() {
        return new ArrayList<>(enginesBySymbol.keySet());
    }

    public static final class UnknownSymbolException extends RuntimeException {
        public UnknownSymbolException(String symbol) {
            super("no matching engine for symbol: " + symbol);
        }
    }
}