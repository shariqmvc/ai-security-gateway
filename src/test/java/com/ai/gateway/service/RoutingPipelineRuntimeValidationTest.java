package com.ai.gateway.service;

import com.ai.gateway.routing.PolicyBasedRoutingStrategy;
import com.ai.gateway.routing.scoring.CandidateScoringEngine;
import com.ai.gateway.routing.selection.CandidateSelectionEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 6.5.8 runtime validation.
 *
 * <p>Verifies that the production Spring container can construct the complete
 * scoring/selection pipeline and the policy routing strategy.</p>
 */
@SpringBootTest
class RoutingPipelineRuntimeValidationTest {

    @Autowired
    private CandidateScoringEngine candidateScoringEngine;

    @Autowired
    private CandidateSelectionEngine candidateSelectionEngine;

    @Autowired
    private PolicyBasedRoutingStrategy policyBasedRoutingStrategy;

    @Test
    void productionRoutingPipelineBeansAreWired() {
        assertNotNull(candidateScoringEngine);
        assertNotNull(candidateSelectionEngine);
        assertNotNull(policyBasedRoutingStrategy);
    }
}
