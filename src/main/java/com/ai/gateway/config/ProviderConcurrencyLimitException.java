package com.ai.gateway.config;

import org.springframework.web.client.ResourceAccessException;

/**
 * Indicates that provider concurrency capacity could not be acquired within
 * the configured wait budget.
 */
public class ProviderConcurrencyLimitException extends ResourceAccessException {

    public ProviderConcurrencyLimitException(String message) {
        super(message);
    }
}
