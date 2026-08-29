package com.ai.gateway.config;

import com.ai.gateway.core.model.Provider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;

/**
 * Provider-specific HTTP request factory.
 *
 * <p>Applies provider-specific connect/read timeout ceilings and further
 * reduces them according to the active ProviderRequestBudget.</p>
 */
@Slf4j
public class ProviderHttpRequestFactory
        extends SimpleClientHttpRequestFactory {

    private final Provider provider;
    private final Duration configuredConnectTimeout;
    private final Duration configuredReadTimeout;

    public ProviderHttpRequestFactory(
            Provider provider,
            ProviderHttpProperties.TimeoutPolicy timeoutPolicy) {

        this.provider = provider;

        this.configuredConnectTimeout =
                timeoutPolicy.getConnectTimeout();

        this.configuredReadTimeout =
                timeoutPolicy.getReadTimeout();

        setConnectTimeout(configuredConnectTimeout);
        setReadTimeout(configuredReadTimeout);

        log.info(
                "PROVIDER_HTTP_TIMEOUT_CONFIG provider={} connectTimeoutMs={} readTimeoutMs={}",
                provider,
                durationMillis(configuredConnectTimeout),
                durationMillis(configuredReadTimeout)
        );
    }

    @Override
    protected void prepareConnection(
            HttpURLConnection connection,
            String httpMethod) throws IOException {

        super.prepareConnection(
                connection,
                httpMethod
        );

        long remainingMillis =
                ProviderRequestBudget.remainingMillis();

        if (remainingMillis == 0L) {
            throw new ProviderRequestBudgetExceededException(
                    "Provider request budget exhausted before invoking "
                            + provider + "."
            );
        }

        /*
         * No active request-level budget.
         * The configured provider timeout remains in effect.
         */
        if (!ProviderRequestBudget.isActive()) {

            log.info(
                    "PROVIDER_HTTP_TIMEOUT_APPLIED provider={} configuredConnectTimeoutMs={} configuredReadTimeoutMs={} effectiveConnectTimeoutMs={} effectiveReadTimeoutMs={} budgetActive=false",
                    provider,
                    durationMillis(configuredConnectTimeout),
                    durationMillis(configuredReadTimeout),
                    connection.getConnectTimeout(),
                    connection.getReadTimeout()
            );

            return;
        }

        int effectiveConnectTimeout =
                effectiveTimeoutMillis(
                        configuredConnectTimeout,
                        remainingMillis
                );

        int effectiveReadTimeout =
                effectiveTimeoutMillis(
                        configuredReadTimeout,
                        remainingMillis
                );

        /*
         * These are the actual values applied to the HttpURLConnection.
         */
        connection.setConnectTimeout(
                effectiveConnectTimeout
        );

        connection.setReadTimeout(
                effectiveReadTimeout
        );

        log.info(
                "PROVIDER_HTTP_TIMEOUT_APPLIED provider={} configuredConnectTimeoutMs={} configuredReadTimeoutMs={} remainingBudgetMs={} effectiveConnectTimeoutMs={} effectiveReadTimeoutMs={}",
                provider,
                durationMillis(configuredConnectTimeout),
                durationMillis(configuredReadTimeout),
                remainingMillis,
                effectiveConnectTimeout,
                effectiveReadTimeout
        );
    }

    private int effectiveTimeoutMillis(
            Duration configured,
            long remainingMillis) {

        long configuredMillis =
                configured == null
                        ? remainingMillis
                        : Math.max(
                        1L,
                        configured.toMillis()
                );

        long effective =
                Math.min(
                        configuredMillis,
                        remainingMillis
                );

        if (effective <= 0L) {
            throw new ProviderRequestBudgetExceededException(
                    "Provider request budget exhausted for "
                            + provider
                            + "."
            );
        }

        return (int) Math.min(
                Integer.MAX_VALUE,
                effective
        );
    }

    private long durationMillis(Duration duration) {
        return duration == null
                ? -1L
                : duration.toMillis();
    }
}