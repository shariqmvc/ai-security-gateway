package com.ai.gateway.core.routing.intelligence;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gateway.routing.capacity")
public class RoutingCapacityProperties {
    private boolean enabled = true;
    private boolean hardLimit = true;
    private int maxParallelRequests = 0;
    private double utilizationThreshold = 0.80;
    private int rpmWindowSeconds = 60;
    private int tpmWindowSeconds = 60;
    private Map<String, Integer> maxParallel = new LinkedHashMap<>();
}
