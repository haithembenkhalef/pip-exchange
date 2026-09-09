package com.exchange.pip.core.shared;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OrderIdGenerator {
    private final IdGenerator delegate = new IdGenerator(); // your existing AtomicLong wrapper

    public long next() {
        return delegate.next();
    }
}