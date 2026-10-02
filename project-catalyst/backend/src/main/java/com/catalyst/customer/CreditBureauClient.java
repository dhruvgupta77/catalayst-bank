package com.catalyst.customer;

/**
 * Port: replace with a real credit bureau integration. Implementations throw ExternalDependencyException.
 */
public interface CreditBureauClient {
    CreditBureauReport fetch(String customerId);
}
