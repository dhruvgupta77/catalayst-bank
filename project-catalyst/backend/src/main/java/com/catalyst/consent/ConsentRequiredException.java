package com.catalyst.consent;

public class ConsentRequiredException extends RuntimeException {
    public ConsentRequiredException(String m) {
        super(m);
    }
}

