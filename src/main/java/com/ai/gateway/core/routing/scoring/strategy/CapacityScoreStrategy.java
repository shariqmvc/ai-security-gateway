package com.ai.gateway.core.routing.scoring.strategy;

import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.scoring.CandidateScoreDimension;
import com.ai.gateway.core.routing.scoring.CandidateScoreStrategy;
import com.ai.gateway.core.routing.scoring.CandidateScoringContext;
import org.springframework.stereotype.Component;

@Component
public class CapacityScoreStrategy implements CandidateScoreStrategy {

    @Override
    public CandidateScoreDimension dimension() {
        return CandidateScoreDimension.CAPACITY;
    }

    @Override
    public double rawScore(RoutingCandidate candidate, CandidateScoringContext context) {
        if (context.runtimeSignals() == null
                || context.runtimeSignals().capacity() == null) {
            return 1.0;
        }

        Double utilization =
                context.runtimeSignals().capacity().utilization().get(candidate.candidateKey());

        if (utilization == null) {
            return 1.0;
        }

        return Math.max(0.0, Math.min(1.0, 1.0 - utilization));
    }
}
