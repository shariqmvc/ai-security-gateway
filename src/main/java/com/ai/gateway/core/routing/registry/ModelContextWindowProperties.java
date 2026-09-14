package com.ai.gateway.core.routing.registry;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gateway.routing.context-windows")
public class ModelContextWindowProperties {

    private int defaultTokens = 16000;
    private Map<String, Integer> providers = new HashMap<>();
    private Map<String, Integer> models = new HashMap<>();

    public int resolve(String provider, String model) {
        if (model != null) {
            Integer exact = models.get(model);
            if (exact != null && exact > 0) {
                return exact;
            }
        }
        if (provider != null) {
            Integer providerDefault = providers.get(provider);
            if (providerDefault == null) {
                providerDefault = providers.get(provider.toUpperCase());
            }
            if (providerDefault != null && providerDefault > 0) {
                return providerDefault;
            }
        }
        return Math.max(256, defaultTokens);
    }

}
