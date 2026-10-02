package com.catalyst.api;

/** A deterministic policy rule blocked a customer request (HTTP 422). */
public class PolicyViolationException extends RuntimeException {
    public PolicyViolationException(String m) {
        super(m);
    }
}