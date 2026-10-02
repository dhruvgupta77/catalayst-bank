package com.catalyst.api;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String m) {
        super(m);
    }
}

