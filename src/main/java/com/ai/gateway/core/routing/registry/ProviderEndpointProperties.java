package com.ai.gateway.core.routing.registry;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

/** System-level executable endpoints keyed by routing endpoint identity. */
@Configuration
@ConfigurationProperties(prefix = "gateway.routing.endpoints")
@Getter
@Setter
public class ProviderEndpointProperties {
    private Map<String, String> urls = new LinkedHashMap<>();
}
