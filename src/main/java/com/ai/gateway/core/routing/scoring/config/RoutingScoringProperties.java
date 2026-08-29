package com.ai.gateway.core.routing.scoring.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gateway.routing.scoring")
public class RoutingScoringProperties {

    private Weights weights = new Weights();

    private Defaults defaults = new Defaults();

    /**
     * Optional configured candidate latency in milliseconds.
     * Key format: PROVIDER:model
     */
    private Map<String, Double> latencyMs = new LinkedHashMap<>();

    /**
     * Optional configured availability preference between 0 and 1.
     * Key format: PROVIDER:model
     */
    private Map<String, Double> availability = new LinkedHashMap<>();

    @Getter
    @Setter
    public static class Weights {
        private double cost = 0.30;
        private double latency = 0.25;
        private double availability = 0.20;
        private double policyPreference = 0.25;
    }

    @Getter
    @Setter
    public static class Defaults {
        private double latencyMs = 1_000.0;
        private double availability = 1.0;
    }
}
