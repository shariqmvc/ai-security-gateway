package com.ai.gateway.routing.scoring.strategy;

import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.scoring.CandidateScoreDimension;
import com.ai.gateway.routing.scoring.CandidateScoreStrategy;
import com.ai.gateway.routing.scoring.CandidateScoringContext;
import com.ai.gateway.routing.policy.RoutingPolicy;
import org.springframework.stereotype.Component;

@Component
public class PolicyPreferenceScoreStrategy implements CandidateScoreStrategy {

    @Override
    public CandidateScoreDimension dimension() {
        return CandidateScoreDimension.POLICY_PREFERENCE;
    }

    @Override
    public double rawScore(
            RoutingCandidate candidate,
            CandidateScoringContext context) {

        RoutingPolicy policy = context.policy();

        boolean providerMatch =
                policy.preferredProvider() != null
                        && policy.preferredProvider() == candidate.provider();

        boolean modelMatch =
                policy.preferredModel() != null
                        && policy.preferredModel().equals(candidate.model());

        if (providerMatch && modelMatch) {
            return 1.0;
        }

        if (providerMatch || modelMatch) {
            return 0.5;
        }

        return 0.0;
    }
}
