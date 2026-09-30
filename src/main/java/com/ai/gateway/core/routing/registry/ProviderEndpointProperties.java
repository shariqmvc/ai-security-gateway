package com.ai.gateway.core.routing.registry;

import com.ai.gateway.core.model.Provider;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/** System-level executable endpoints keyed by stable endpoint identity. */
@Configuration
@ConfigurationProperties(prefix = "gateway.routing.endpoints")
@Getter
@Setter
public class ProviderEndpointProperties {
    private Map<String, Endpoint> definitions = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Endpoint {
        private Provider provider;
        private String url;
        private boolean enabled = true;
    }
}
