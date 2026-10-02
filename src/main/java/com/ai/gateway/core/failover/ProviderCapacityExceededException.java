package com.ai.gateway.core.failover;

public class ProviderCapacityExceededException extends RuntimeException {

    public ProviderCapacityExceededException(String message) {
        super(message);
    }
}
