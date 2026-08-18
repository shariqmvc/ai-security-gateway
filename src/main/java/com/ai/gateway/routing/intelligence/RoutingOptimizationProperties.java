package com.ai.gateway.routing.intelligence;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gateway.routing.optimization")
public class RoutingOptimizationProperties {
    private boolean enabled = true;
    private double degradedAvailabilityBoost = 1.25;
    private double unhealthyAvailabilityBoost = 1.35;
    private double latencyPriorityBoost = 1.15;
    private double costPriorityBoost = 1.15;
    private double reliabilityPriorityBoost = 1.15;
    /** Maximum number of candidates retained for final deterministic ranking. */
    private int topK = 5;
    /** Enable Pareto-dominance pruning before bounded Top-K selection. */
    private boolean paretoEnabled = true;
    /** Pareto pruning is bounded to avoid O(C^2) work for very large sets. */
    private int paretoMaxCandidates = 64;
}
