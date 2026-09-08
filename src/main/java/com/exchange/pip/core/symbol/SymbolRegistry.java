package com.exchange.pip.core.symbol;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central lookup for symbol configuration. The API layer consults this
 * to convert human-readable prices/quantities into raw longs before
 * they ever reach the matching engine, and back again when reporting
 * results to the outside world.
 */
public final class SymbolRegistry {

    private final Map<String, SymbolSpecification> symbols = new ConcurrentHashMap<>();

    public void register(SymbolSpecification spec) {
        symbols.put(spec.getSymbol(), spec);
    }

    public SymbolSpecification get(String symbol) {
        SymbolSpecification spec = symbols.get(symbol);
        if (spec == null) {
            throw new IllegalArgumentException("unknown symbol: " + symbol);
        }
        return spec;
    }

    public boolean exists(String symbol) {
        return symbols.containsKey(symbol);
    }
}