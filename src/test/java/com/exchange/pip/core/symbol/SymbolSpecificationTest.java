package com.exchange.pip.core.symbol;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class SymbolSpecificationTest {

    // BTC-USD: price in cents (2 decimals), qty in satoshis (8 decimals)
    // tickSize=1 (any cent), lotSize=10_000 (0.0001 BTC steps)
    // qty bounds: 0.0001 BTC .. 100 BTC ; price bounds: $1.00 .. $10,000,000.00
    private SymbolSpecification btcUsd() {
        return new SymbolSpecification(
            "BTC-USD", 2, 8,
            1L, 10_000L,
            10_000L, 100_00000000L,
            100L, 1_000_000_000_00L
        );
    }

    @Test
    void convertsHumanPriceToRawAndBack() {
        SymbolSpecification spec = btcUsd();

        long raw = spec.toRawPrice(new BigDecimal("50000.00"));
        assertEquals(5_000_000L, raw);

        BigDecimal back = spec.toHumanPrice(raw);
        assertEquals(0, back.compareTo(new BigDecimal("50000.00")));
    }

    @Test
    void convertsHumanQuantityToRawAndBack() {
        SymbolSpecification spec = btcUsd();

        long raw = spec.toRawQuantity(new BigDecimal("0.01")); // 0.01 BTC
        assertEquals(1_000_000L, raw); // 1,000,000 satoshis

        BigDecimal back = spec.toHumanQuantity(raw);
        assertEquals(0, back.compareTo(new BigDecimal("0.01")));
    }

    @Test
    void registryRejectsUnknownSymbol() {
        SymbolRegistry registry = new SymbolRegistry();
        assertThrows(IllegalArgumentException.class, () -> registry.get("DOES-NOT-EXIST"));
    }

    @Test
    void registryReturnsRegisteredSpec() {
        SymbolRegistry registry = new SymbolRegistry();
        registry.register(btcUsd());

        assertTrue(registry.exists("BTC-USD"));
        assertEquals(8, registry.get("BTC-USD").getQuantityScale());
    }

    @Test
    void validateAcceptsValidPriceAndQty() {
        SymbolSpecification spec = btcUsd();
        assertDoesNotThrow(() -> spec.validate(5_000_000L, 1_000_000L));
    }

    @Test
    void validateRejectsPriceBelowMin() {
        SymbolSpecification spec = btcUsd();
        assertThrows(IllegalArgumentException.class, () -> spec.validate(50L, 1_000_000L));
    }

    @Test
    void validateRejectsPriceAboveMax() {
        SymbolSpecification spec = btcUsd();
        assertThrows(IllegalArgumentException.class, () -> spec.validate(2_000_000_000_00L, 1_000_000L));
    }

    @Test
    void validateRejectsQtyNotMultipleOfLotSize() {
        SymbolSpecification spec = btcUsd();
        // lotSize is 10_000; 1_000_005 is not a multiple
        assertThrows(IllegalArgumentException.class, () -> spec.validate(5_000_000L, 1_000_005L));
    }

    @Test
    void validateRejectsQtyBelowMinOrderQty() {
        SymbolSpecification spec = btcUsd();
        assertThrows(IllegalArgumentException.class, () -> spec.validate(5_000_000L, 1L));
    }

    @Test
    void validateRejectsQtyAboveMaxOrderQty() {
        SymbolSpecification spec = btcUsd();
        assertThrows(IllegalArgumentException.class, () -> spec.validate(5_000_000L, 200_00000000L));
    }

    @Test
    void validateQuantityOnlySkipsPriceChecks() {
        SymbolSpecification spec = btcUsd();
        // price=0 would fail validate(), but validateQuantityOnly ignores price entirely
        assertDoesNotThrow(() -> spec.validateQuantityOnly(1_000_000L));
    }

    @Test
    void constructorRejectsNonPositiveTickOrLotSize() {
        assertThrows(IllegalArgumentException.class, () ->
            new SymbolSpecification("X", 2, 8, 0L, 10_000L, 10_000L, 100_00000000L, 100L, 1_000_000_00L));
        assertThrows(IllegalArgumentException.class, () ->
            new SymbolSpecification("X", 2, 8, 1L, 0L, 10_000L, 100_00000000L, 100L, 1_000_000_00L));
    }

    @Test
    void constructorRejectsInvertedBounds() {
        assertThrows(IllegalArgumentException.class, () ->
            new SymbolSpecification("X", 2, 8, 1L, 10_000L, 100_00000000L, 10_000L, 100L, 1_000_000_00L));
        assertThrows(IllegalArgumentException.class, () ->
            new SymbolSpecification("X", 2, 8, 1L, 10_000L, 10_000L, 100_00000000L, 1_000_000_00L, 100L));
    }
}