package com.ai.gateway.failover;

import com.ai.gateway.enums.Provider;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.routing.failover")
public class FailoverProperties {

    private boolean enabled = false;

    /**
     * Maximum number of provider attempts, including the primary provider.
     */
    private int maxAttempts = 2;

    /**
     * Ordered fallback providers for each primary provider.
     */
    private Map<Provider, List<Provider>> providers =
            new EnumMap<>(Provider.class);

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
}
