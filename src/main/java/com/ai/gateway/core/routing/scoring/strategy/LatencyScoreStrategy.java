package com.ai.gateway.core.routing.scoring.strategy;

import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.scoring.CandidateScoreDimension;
import com.ai.gateway.core.routing.scoring.CandidateScoreStrategy;
import com.ai.gateway.core.routing.scoring.CandidateScoringContext;
import com.ai.gateway.core.routing.scoring.config.RoutingScoringProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LatencyScoreStrategy implements CandidateScoreStrategy {

    private final RoutingScoringProperties properties;

    @Override
    public CandidateScoreDimension dimension() {
        return CandidateScoreDimension.LATENCY;
    }

    @Override
    public double rawScore(
            RoutingCandidate candidate,
            CandidateScoringContext context) {

        Double runtime = context.runtimeSignals().latencyMs().get(key(candidate));
        if (runtime != null) return runtime;
        return properties.getLatencyMs().getOrDefault(
                key(candidate),
                properties.getDefaults().getLatencyMs());
    }

    @Override
    public boolean lowerIsBetter() {
        return true;
    }

    private String key(RoutingCandidate candidate) {
        return candidate.provider().name() + ":" + candidate.model();
    }
}
