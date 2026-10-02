package com.catalyst.api;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String m) {
        super(m);
    }
}

