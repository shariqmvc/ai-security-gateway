package com.ai.gateway.routing.health.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gateway.routing.health")
public class RoutingHealthProperties {

    private boolean enabled = true;
    private boolean rejectUnhealthy = true;
    private int minObservations = 3;
    private long consecutiveFailureThreshold = 3;
    private double degradedAvailability = 0.90;
    private double unhealthyAvailability = 0.70;
    private double ewmaAlpha = 0.20;
    private long signalTtlSeconds = 300;
}
