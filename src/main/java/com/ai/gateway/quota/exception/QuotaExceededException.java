package com.ai.gateway.quota.exception;

public class QuotaExceededException
        extends RuntimeException {

    public QuotaExceededException(
            String message) {

        super(message);
    }
}