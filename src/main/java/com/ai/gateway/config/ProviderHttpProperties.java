package com.ai.gateway.config;

import com.ai.gateway.enums.Provider;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Outbound provider HTTP timeout policy.
 *
 * <p>The gateway keeps a conservative default while allowing provider/model
 * classes with materially different runtime characteristics to use their own
 * connect/read ceilings. A request-level deadline can further reduce the
 * effective timeout for the current attempt.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.provider.http")
public class ProviderHttpProperties {

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(30);

    private Map<Provider, TimeoutPolicy> providers =
            new EnumMap<>(Provider.class);

    public TimeoutPolicy forProvider(Provider provider) {
        TimeoutPolicy configured =
                provider == null || providers == null
                        ? null
                        : providers.get(provider);

        if (configured == null) {
            return new TimeoutPolicy(connectTimeout, readTimeout);
        }

        return new TimeoutPolicy(
                configured.getConnectTimeout() != null
                        ? configured.getConnectTimeout()
                        : connectTimeout,
                configured.getReadTimeout() != null
                        ? configured.getReadTimeout()
                        : readTimeout);
    }

    @Getter
    @Setter
    public static class TimeoutPolicy {
        private Duration connectTimeout;
        private Duration readTimeout;

        public TimeoutPolicy() {
        }

        public TimeoutPolicy(
                Duration connectTimeout,
                Duration readTimeout) {
            this.connectTimeout = connectTimeout;
            this.readTimeout = readTimeout;
        }
    }
}
