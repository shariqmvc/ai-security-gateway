package com.ai.gateway.core.failover;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Configuration for the local provider circuit breaker.
 *
 * <p>The circuit breaker is intentionally instance-local. Durable routing
 * health remains the responsibility of the routing-health subsystem.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.routing.failover.circuit-breaker")
public class ProviderCircuitBreakerProperties {

    private boolean enabled = true;

    /**
     * Number of retryable failures required before opening the circuit.
     *
     * <p>The default of one is intentional: a completed long timeout is strong
     * evidence that immediately retrying the same provider is undesirable.</p>
     */
    private int failureThreshold = 1;

    private Duration defaultOpenDuration = Duration.ofSeconds(60);

    private Duration timeoutOpenDuration = Duration.ofSeconds(60);

    private Duration networkOpenDuration = Duration.ofSeconds(30);

    private Duration rateLimitOpenDuration = Duration.ofSeconds(15);

    private Duration serverErrorOpenDuration = Duration.ofSeconds(30);

    long openDurationMs(ProviderFailureCategory category) {

        Duration duration = switch (category) {

            case TIMEOUT ->
                    timeoutOpenDuration;

            case NETWORK ->
                    networkOpenDuration;

            case RATE_LIMITED ->
                    rateLimitOpenDuration;

            case SERVER_ERROR ->
                    serverErrorOpenDuration;

            case CLIENT_ERROR, REQUEST_BUDGET_EXHAUSTED, MEDIA_INPUT ->
                    Duration.ZERO;

            case UNKNOWN ->
                    defaultOpenDuration;
        };

        return Math.max(0L, duration.toMillis());
    }
}