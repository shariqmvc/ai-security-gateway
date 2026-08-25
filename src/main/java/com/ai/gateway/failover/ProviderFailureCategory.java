package com.ai.gateway.failover;

/**
 * Normalized provider failure categories used for retry and circuit-breaker policy.
 */
public enum ProviderFailureCategory {
    /**
     * Caller-supplied media could not be fetched or validated. This must not
     * affect provider health and must never trigger provider failover.
     */
    MEDIA_INPUT,
    NETWORK,
    TIMEOUT,
    RATE_LIMITED,
    CLIENT_ERROR,
    SERVER_ERROR,
    UNKNOWN,
    REQUEST_BUDGET_EXHAUSTED
}
