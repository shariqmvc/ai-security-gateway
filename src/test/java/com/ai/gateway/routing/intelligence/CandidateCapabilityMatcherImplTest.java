package com.ai.gateway.routing.intelligence;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.registry.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
@ActiveProfiles("test")
class CandidateCapabilityMatcherImplTest {
    @Test
    void filtersCandidatesThatDoNotSupportRequiredCapabilities() {
        ModelRegistry registry = mock(ModelRegistry.class);
        when(registry.find(Provider.OPENAI, "gpt-a"))
                .thenReturn(java.util.Optional.of(new ModelDefinition(Provider.OPENAI, "gpt-a", "A", ModelStatus.ENABLED, Set.of("CHAT", "REASONING"))));
        when(registry.find(Provider.GEMINI, "gemini-b"))
                .thenReturn(java.util.Optional.of(new ModelDefinition(Provider.GEMINI, "gemini-b", "B", ModelStatus.ENABLED, Set.of("CHAT"))));

        CandidateCapabilityMatcherImpl matcher = new CandidateCapabilityMatcherImpl(registry);
        RoutingDecisionContext context = new RoutingDecisionContext(
                UUID.randomUUID(), "T", null, null, Set.of("REASONING"), false, false, null, RoutingPriority.BALANCED);

        var result = matcher.filter(
                List.of(new RoutingCandidate(Provider.OPENAI, "gpt-a"), new RoutingCandidate(Provider.GEMINI, "gemini-b")),
                context);

        assertEquals(List.of(new RoutingCandidate(Provider.OPENAI, "gpt-a")), result);
    }
}
