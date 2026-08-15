package com.ai.gateway.routing.health;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.health.config.RoutingHealthProperties;
import com.ai.gateway.routing.health.entity.RoutingHealthProfile;
import com.ai.gateway.routing.health.repository.RoutingHealthProfileRepository;
import com.ai.gateway.routing.health.repository.RoutingOutcomeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoutingHealthServiceImplTest {

    @Mock
    private RoutingHealthProfileRepository profiles;

    @Mock
    private RoutingOutcomeRepository outcomes;

    private RoutingHealthProperties properties;
    private RoutingHealthServiceImpl service;
    private RoutingCandidate candidate;

    @BeforeEach
    void setUp() {
        properties = new RoutingHealthProperties();
        properties.setMinObservations(3);
        properties.setConsecutiveFailureThreshold(3);
        properties.setDegradedAvailability(0.90);
        properties.setUnhealthyAvailability(0.70);
        properties.setEwmaAlpha(0.20);
        properties.setSignalTtlSeconds(300);

        service = new RoutingHealthServiceImpl(
                profiles,
                outcomes,
                properties
        );

        candidate = new RoutingCandidate(
                Provider.OPENAI,
                "gpt-5"
        );
    }

    @Test
    void firstObservationIsUnknown() {
        when(outcomes.findTop100ByProviderAndModelOrderByCreatedAtDesc(
                Provider.OPENAI,
                "gpt-5"))
                .thenReturn(List.of());

        when(profiles.save(any(RoutingHealthProfile.class)))
                .thenAnswer(i -> i.getArgument(0));

        service.recordSuccess(candidate, 250);

        RoutingHealthProfile saved = verifySaved();

        assertEquals(
                RoutingHealthStatus.UNKNOWN,
                saved.getHealthStatus()
        );

        assertEquals(1, saved.getSuccessCount());
        assertEquals(0, saved.getConsecutiveFailures());
        assertEquals(250.0, saved.getEwmaLatencyMs());
    }

    @Test
    void consecutiveFailuresMakeHealthyCandidateUnhealthy() {
        RoutingHealthProfile profile = profile(3, 0, 0.0);

        when(profiles.findByProviderAndModel(
                Provider.OPENAI,
                "gpt-5"))
                .thenReturn(Optional.of(profile));

        when(profiles.save(any(RoutingHealthProfile.class)))
                .thenAnswer(i -> i.getArgument(0));

        service.recordFailure(candidate, "TIMEOUT");
        service.recordFailure(candidate, "TIMEOUT");
        service.recordFailure(candidate, "TIMEOUT");

        assertEquals(
                RoutingHealthStatus.UNHEALTHY,
                profile.getHealthStatus()
        );

        assertEquals(
                3,
                profile.getConsecutiveFailures()
        );

        assertFalse(
                service.isHealthyForRouting(candidate)
        );
    }

    @Test
    void staleUnhealthySignalDoesNotBlockRouting() {
        RoutingHealthProfile profile = profile(0, 5, 0.0);

        profile.setHealthStatus(
                RoutingHealthStatus.UNHEALTHY
        );

        profile.setLastObservedAt(
                LocalDateTime.now().minusHours(2)
        );

        when(profiles.findByProviderAndModel(
                Provider.OPENAI,
                "gpt-5"))
                .thenReturn(Optional.of(profile));

        assertTrue(
                service.isHealthyForRouting(candidate)
        );
    }

    private RoutingHealthProfile profile(
            long success,
            long failures,
            double availability
    ) {
        return RoutingHealthProfile.builder()
                .provider(Provider.OPENAI)
                .model("gpt-5")
                .healthStatus(RoutingHealthStatus.UNKNOWN)
                .successCount(success)
                .failureCount(failures)
                .consecutiveFailures(failures)
                .availability(availability)
                .lastObservedAt(LocalDateTime.now())
                .build();
    }

    private RoutingHealthProfile verifySaved() {
        var captor =
                org.mockito.ArgumentCaptor.forClass(
                        RoutingHealthProfile.class
                );

        verify(profiles).save(captor.capture());

        return captor.getValue();
    }
}