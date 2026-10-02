package com.catalyst.customer;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Last-known-good bureau data used when the bureau is unavailable.
 */
@Component
public class BureauCache {
    private final Map<String, CreditBureauReport> cache = new ConcurrentHashMap<>();

    public void put(CreditBureauReport r) {
        cache.put(r.customerId(), r);
    }

    public Optional<CreditBureauReport> get(String customerId) {
        return Optional.ofNullable(cache.get(customerId));
    }

    public void clear() {
        cache.clear();
    }
}
