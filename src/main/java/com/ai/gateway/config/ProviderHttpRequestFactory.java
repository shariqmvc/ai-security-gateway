package com.ai.gateway.config;

import com.ai.gateway.enums.Provider;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.time.Duration;

/**
 * Provider-specific HTTP request factory that additionally honors the current
 * request-level provider deadline.
 */
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
    }

    @Override
    protected void prepareConnection(
            HttpURLConnection connection,
            String httpMethod) throws IOException {

        super.prepareConnection(connection, httpMethod);

        long remainingMillis = ProviderRequestBudget.remainingMillis();
        if (remainingMillis == 0L) {
            throw new ProviderRequestBudgetExceededException(
                    "Provider request budget exhausted before invoking "
                            + provider + ".");
        }

        if (!ProviderRequestBudget.isActive()) {
            return;
        }

        int effectiveConnectTimeout =
                effectiveTimeoutMillis(
                        configuredConnectTimeout,
                        remainingMillis);

        int effectiveReadTimeout =
                effectiveTimeoutMillis(
                        configuredReadTimeout,
                        remainingMillis);

        connection.setConnectTimeout(effectiveConnectTimeout);
        connection.setReadTimeout(effectiveReadTimeout);
    }

    private int effectiveTimeoutMillis(
            Duration configured,
            long remainingMillis) {

        long configuredMillis =
                configured == null
                        ? remainingMillis
                        : Math.max(1L, configured.toMillis());

        long effective =
                Math.min(configuredMillis, remainingMillis);

        if (effective <= 0L) {
            throw new ProviderRequestBudgetExceededException(
                    "Provider request budget exhausted for " + provider + ".");
        }

        return (int) Math.min(Integer.MAX_VALUE, effective);
    }
}
