package com.ai.gateway.core.routing.intelligence;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.ChatRequest;
import com.ai.gateway.core.routing.RoutingContext;
import com.ai.gateway.core.routing.policy.RoutingPolicy;
import com.ai.gateway.core.routing.scoring.config.RoutingScoringProperties;
import com.ai.gateway.core.routing.scoring.CandidateScoreDimension;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.ActiveProfiles;

import java.util.Set;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
@ActiveProfiles("test")
class RoutingDecisionIntelligenceServiceTest {
    @Test
    void buildsContextFromExtensiveResearchRequest() {
        RoutingScoringProperties properties = new RoutingScoringProperties();
        UnityRoutingProperties unity = new UnityRoutingProperties();
        unity.setEnabled(true);
        var capabilityMatcher = Mockito.mock(CandidateCapabilityMatcher.class);
        var runtime = new RoutingRuntimeSignalService(properties);
        var adaptive = new AdaptiveRoutingScoringService(properties);
        var service = new RoutingDecisionIntelligenceService(capabilityMatcher, adaptive, runtime, unity);

        ChatRequest request = ChatRequest.builder()
                .prompt("research")
                .requiredCapabilities(Set.of("REASONING"))
                .extensiveResearch(true)
                .executionRole("research-synthesis")
                .routingPriority("RELIABILITY")
                .build();

        AuthenticationContext auth = AuthenticationContext.builder().tenantCode("T").build();
        RoutingDecisionContext context = service.context(new RoutingContext(request, auth));

        assertTrue(context.extensiveResearchRequested());
        assertTrue(context.extensiveResearchEnabled());
        assertEquals("research-synthesis", context.executionRole());
        assertEquals(RoutingPriority.RELIABILITY, context.routingPriority());
        assertEquals(Set.of("REASONING"), context.requiredCapabilities());

        var scoring = service.scoringContext(new RoutingPolicy(true, List.of(), List.of(), null, null), context);
        assertTrue(scoring.extensiveResearchEnabled());
        assertTrue(scoring.weightOverrides().get(CandidateScoreDimension.AVAILABILITY) > 0.20);
    }
}
