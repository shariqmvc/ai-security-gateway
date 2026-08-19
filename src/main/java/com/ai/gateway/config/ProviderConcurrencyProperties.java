package com.ai.gateway.config;

import com.ai.gateway.enums.Provider;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Optional provider-level concurrency controls.
 *
 * <p>A max-concurrent value of zero disables the limiter and preserves the
 * existing behavior. Controls are provider-wide, so all tenants share the
 * same provider capacity guard while tenant identity remains in the request
 * context.</p>
 */
@Getter
@Setter
@Component
@Slf4j
@ConfigurationProperties(prefix = "gateway.provider.concurrency")
public class ProviderConcurrencyProperties {

    private Map<Provider, LimitPolicy> providers =
            new EnumMap<>(Provider.class);

    public LimitPolicy forProvider(Provider provider) {
        LimitPolicy policy = providers == null || provider == null
                ? null
                : providers.get(provider);
        return policy == null ? new LimitPolicy() : policy;
    }

    @Getter
    @Setter
    public static class LimitPolicy {
        /** 0 disables the provider concurrency limiter. */
        private int maxConcurrent = 0;

        /**
         * Maximum time to wait for a provider permit. Zero means do not wait.
         * The effective wait is further capped by the active request budget.
         */
        private Duration acquireTimeout = Duration.ZERO;
    }

    @PostConstruct
    public void logConfiguration() {
        LimitPolicy ollama = forProvider(Provider.OLLAMA);

        log.info(
                "P4.3 provider concurrency configuration: provider=OLLAMA maxConcurrent={} acquireTimeout={}",
                ollama.getMaxConcurrent(),
                ollama.getAcquireTimeout()
        );
    }
}
