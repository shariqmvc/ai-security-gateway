package com.ai.gateway.routing.health;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.engine.RoutingCandidate;
import com.ai.gateway.core.routing.health.RoutingHealthServiceImpl;
import com.ai.gateway.core.routing.health.RoutingHealthStatus;
import com.ai.gateway.core.routing.health.RoutingOutcomeReader;
import com.ai.gateway.core.routing.health.config.RoutingHealthProperties;
import com.ai.gateway.core.routing.health.entity.RoutingHealthProfile;
import com.ai.gateway.core.routing.health.repository.RoutingHealthProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class RoutingHealthServiceImplTest {

    @Mock
    private RoutingHealthProfileRepository profiles;

    @Mock
    private RoutingOutcomeReader outcomes;

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
        when(outcomes.findRecent(
                Provider.OPENAI,
                "gpt-5"))
                .thenReturn(List.of());

        when(profiles.findByProviderAndModel(
                Provider.OPENAI,
                "gpt-5"))
                .thenReturn(Optional.empty());

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

        when(outcomes.findRecent(
                Provider.OPENAI,
                "gpt-5"))
                .thenReturn(List.of());

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