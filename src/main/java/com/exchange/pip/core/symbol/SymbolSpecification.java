package com.exchange.pip.core.symbol;

import java.math.BigDecimal;

/**
 * Defines what a raw integer price/quantity actually MEANS for a given
 * symbol. The matching engine only ever works with raw longs — this is
 * the config that lets the boundary (API layer) convert between
 * human-readable decimals and the engine's internal integers.
 *
 * Example: BTC-USD with priceScale = 2 means price=5_000_000L represents
 * $50,000.00 (2 decimal places). quantityScale = 8 means a quantity is
 * expressed in units of 1E-8 BTC (satoshis).
 *
 * The engine itself never reads priceScale/quantityScale during matching —
 * it only compares raw longs. This class exists purely for the
 * human <-> raw conversion at the system boundary.
 */
public final class SymbolSpecification {

    private final String symbol;
    private final int priceScale;      // number of decimal places a raw price represents
    private final int quantityScale;   // number of decimal places a raw quantity represents

    // Validation fields — these operate on RAW longs, same units as Order.price/quantity.
    // They are distinct from priceScale/quantityScale: scale says "how many decimals",
    // these say "what step size / bounds are actually allowed at that scale".
    private final long tickSize;       // minimum price increment; price must be a multiple of this
    private final long lotSize;        // minimum quantity increment; qty must be a multiple of this
    private final long minOrderQty;    // smallest quantity an order may have
    private final long maxOrderQty;    // largest quantity an order may have
    private final long minPrice;       // lowest acceptable limit price (sanity bound)
    private final long maxPrice;       // highest acceptable limit price (sanity bound)

    public SymbolSpecification(String symbol, int priceScale, int quantityScale,
                                long tickSize, long lotSize,
                                long minOrderQty, long maxOrderQty,
                                long minPrice, long maxPrice) {
        if (priceScale < 0 || quantityScale < 0) {
            throw new IllegalArgumentException("scale must be >= 0");
        }
        if (tickSize <= 0 || lotSize <= 0) {
            throw new IllegalArgumentException("tickSize and lotSize must be positive");
        }
        if (minOrderQty <= 0 || maxOrderQty < minOrderQty) {
            throw new IllegalArgumentException("invalid order qty bounds");
        }
        if (minPrice <= 0 || maxPrice < minPrice) {
            throw new IllegalArgumentException("invalid price bounds");
        }
        if (minOrderQty % lotSize != 0 || maxOrderQty % lotSize != 0) {
            throw new IllegalArgumentException("order qty bounds must be multiples of lotSize");
        }
        if (minPrice % tickSize != 0 || maxPrice % tickSize != 0) {
            throw new IllegalArgumentException("price bounds must be multiples of tickSize");
        }
        this.symbol = symbol;
        this.priceScale = priceScale;
        this.quantityScale = quantityScale;
        this.tickSize = tickSize;
        this.lotSize = lotSize;
        this.minOrderQty = minOrderQty;
        this.maxOrderQty = maxOrderQty;
        this.minPrice = minPrice;
        this.maxPrice = maxPrice;
    }

    public String getSymbol() { return symbol; }
    public int getPriceScale() { return priceScale; }
    public int getQuantityScale() { return quantityScale; }
    public long getTickSize() { return tickSize; }
    public long getLotSize() { return lotSize; }
    public long getMinOrderQty() { return minOrderQty; }
    public long getMaxOrderQty() { return maxOrderQty; }
    public long getMinPrice() { return minPrice; }
    public long getMaxPrice() { return maxPrice; }

    /**
     * Validates a raw price/quantity pair against this symbol's rules.
     * Call this before an order is allowed to enter the engine — the
     * matching engine itself assumes it never sees invalid values.
     *
     * @throws IllegalArgumentException with a specific reason if invalid
     */
    public void validate(long price, long quantity) {
        if (price < minPrice || price > maxPrice) {
            throw new IllegalArgumentException(
                "price %d out of bounds [%d, %d]".formatted(price, minPrice, maxPrice));
        }
        if (price % tickSize != 0) {
            throw new IllegalArgumentException(
                "price %d is not a multiple of tickSize %d".formatted(price, tickSize));
        }
        if (quantity < minOrderQty || quantity > maxOrderQty) {
            throw new IllegalArgumentException(
                "quantity %d out of bounds [%d, %d]".formatted(quantity, minOrderQty, maxOrderQty));
        }
        if (quantity % lotSize != 0) {
            throw new IllegalArgumentException(
                "quantity %d is not a multiple of lotSize %d".formatted(quantity, lotSize));
        }
    }

    /** Same as {@link #validate(long, long)} but skips price checks — for MARKET orders. */
    public void validateQuantityOnly(long quantity) {
        if (quantity < minOrderQty || quantity > maxOrderQty) {
            throw new IllegalArgumentException(
                "quantity %d out of bounds [%d, %d]".formatted(quantity, minOrderQty, maxOrderQty));
        }
        if (quantity % lotSize != 0) {
            throw new IllegalArgumentException(
                "quantity %d is not a multiple of lotSize %d".formatted(quantity, lotSize));
        }
    }

    /** Converts a human-readable decimal price into the engine's raw long. */
    public long toRawPrice(BigDecimal humanPrice) {
        return humanPrice.movePointRight(priceScale).longValueExact();
    }

    /** Converts the engine's raw long price back into a human-readable decimal. */
    public BigDecimal toHumanPrice(long rawPrice) {
        return BigDecimal.valueOf(rawPrice, priceScale);
    }

    /** Converts a human-readable decimal quantity into the engine's raw long. */
    public long toRawQuantity(BigDecimal humanQuantity) {
        return humanQuantity.movePointRight(quantityScale).longValueExact();
    }

    /** Converts the engine's raw long quantity back into a human-readable decimal. */
    public BigDecimal toHumanQuantity(long rawQuantity) {
        return BigDecimal.valueOf(rawQuantity, quantityScale);
    }

    @Override
    public String toString() {
        return "SymbolSpecification{symbol=%s, priceScale=%d, quantityScale=%d, tickSize=%d, lotSize=%d, qty=[%d,%d], price=[%d,%d]}"
            .formatted(symbol, priceScale, quantityScale, tickSize, lotSize, minOrderQty, maxOrderQty, minPrice, maxPrice);
    }
}