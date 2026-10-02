package com.catalyst.customer;

public class ExternalDependencyException extends RuntimeException {
    public ExternalDependencyException(String message) {
        super(message);
    }
}
