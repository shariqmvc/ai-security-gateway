package com.ai.gateway.core.failover;

import com.ai.gateway.core.model.Provider;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.routing.failover")
public class FailoverProperties {

    private boolean enabled = true;

    /**
     * Maximum number of provider attempts, including the primary provider.
     */
    private int maxAttempts = 2;

    /**
     * End-to-end budget shared by the primary and all fallback attempts.
     * Provider HTTP clients automatically reduce their effective timeout to
     * the remaining portion of this budget.
     */
    private Duration requestTimeBudget = Duration.ofSeconds(45);

    /**
     * Do not start another provider attempt when less than this amount of
     * request budget remains. This avoids spending routing/serialization work
     * on an attempt that cannot meaningfully complete.
     */
    private Duration minimumFallbackBudget = Duration.ofSeconds(1);

    /**
     * Ordered fallback providers for each primary provider.
     */
    private Map<Provider, List<Provider>> providers =
            new EnumMap<>(Provider.class);

    /**
     * Controlled provider-runtime failure injection for local/integration validation.
     * Disabled by default and intended only for controlled testing.
     */
    private FailureInjection failureInjection = new FailureInjection();

    public List<Provider> fallbacksFor(Provider provider) {
        if (provider == null || providers == null) {
            return List.of();
        }

        List<Provider> configured = providers.get(provider);
        if (configured == null || configured.isEmpty()) {
            return List.of();
        }

        return new ArrayList<>(configured);
    }

    @Getter
    @Setter
    public static class FailureInjection {
        private boolean enabled = false;
        private Provider provider;
        private String model;
        private String failureType = "PROVIDER_ERROR";

        public boolean matches(Provider provider, String model) {
            return enabled
                    && this.provider != null
                    && this.provider == provider
                    && this.model != null
                    && this.model.equals(model);
        }
    }
}
