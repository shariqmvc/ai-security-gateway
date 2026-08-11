package com.ai.gateway.ratelimit.config;

public final class RateLimitConstants {

    private RateLimitConstants() {
    }

    public static final long DEFAULT_CAPACITY = 100;

    public static final long DEFAULT_REFILL_TOKENS = 100;

    public static final long DEFAULT_REFILL_DURATION_SECONDS = 60;

}
