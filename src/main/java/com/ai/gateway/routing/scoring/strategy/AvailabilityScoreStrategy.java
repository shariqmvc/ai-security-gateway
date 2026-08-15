package com.ai.gateway.routing.scoring.strategy;

import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.scoring.CandidateScoreDimension;
import com.ai.gateway.routing.scoring.CandidateScoreStrategy;
import com.ai.gateway.routing.scoring.CandidateScoringContext;
import com.ai.gateway.routing.scoring.config.RoutingScoringProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AvailabilityScoreStrategy implements CandidateScoreStrategy {

    private final RoutingScoringProperties properties;

    @Override
    public CandidateScoreDimension dimension() {
        return CandidateScoreDimension.AVAILABILITY;
    }

    @Override
    public double rawScore(
            RoutingCandidate candidate,
            CandidateScoringContext context) {

        double value = properties.getAvailability().getOrDefault(
                key(candidate),
                properties.getDefaults().getAvailability());

        return Math.max(0.0, Math.min(1.0, value));
    }

    private String key(RoutingCandidate candidate) {
        return candidate.provider().name() + ":" + candidate.model();
    }
}
