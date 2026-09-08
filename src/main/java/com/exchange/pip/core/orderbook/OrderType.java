package com.exchange.pip.core.orderbook;

public enum OrderType {
    GTC,    // Good-Till-Cancel: remainder rests on the book
    IOC,    // Immediate-or-Cancel: remainder is discarded
    FOK,    // Fill-or-Kill: must fill completely or not at all
    MARKET  // no limit price; sweeps the book until filled or exhausted
}
