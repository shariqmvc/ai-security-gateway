package com.ai.gateway.failover;

/**
 * Normalized provider failure categories used for retry and circuit-breaker policy.
 */
public enum ProviderFailureCategory {
    NETWORK,
    TIMEOUT,
    RATE_LIMITED,
    CLIENT_ERROR,
    SERVER_ERROR,
    UNKNOWN,
    REQUEST_BUDGET_EXHAUSTED
}
