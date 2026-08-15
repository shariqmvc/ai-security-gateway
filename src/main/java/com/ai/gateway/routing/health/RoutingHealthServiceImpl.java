package com.ai.gateway.routing.health;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.engine.RoutingCandidate;
import com.ai.gateway.routing.health.config.RoutingHealthProperties;
import com.ai.gateway.routing.health.entity.RoutingHealthProfile;
import com.ai.gateway.routing.health.entity.RoutingOutcome;
import com.ai.gateway.routing.health.repository.RoutingHealthProfileRepository;
import com.ai.gateway.routing.health.repository.RoutingOutcomeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RoutingHealthServiceImpl implements RoutingHealthService {

    private final RoutingHealthProfileRepository profileRepository;
    private final RoutingOutcomeRepository outcomeRepository;
    private final RoutingHealthProperties properties;

    @Override
    @Transactional(readOnly = true)
    public RoutingHealthSnapshot snapshot(RoutingCandidate candidate) {
        if (candidate == null) {
            return null;
        }
        return profileRepository.findByProviderAndModel(candidate.provider(), candidate.model())
                .map(this::toSnapshot)
                .orElseGet(() -> new RoutingHealthSnapshot(
                        candidate.provider(), candidate.model(),
                        RoutingHealthStatus.UNKNOWN, 0, 0, 0,
                        1.0, 0.0, 0.0, null, false));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoutingHealthSnapshot> snapshots() {
        return profileRepository.findAllByOrderByUpdatedAtDesc()
                .stream().map(this::toSnapshot).toList();
    }

    @Override
    @Transactional
    public void recordSuccess(RoutingCandidate candidate, long latencyMs) {
        if (!properties.isEnabled() || candidate == null) return;

        LocalDateTime now = LocalDateTime.now();
        RoutingHealthProfile profile = getOrCreate(candidate);
        double alpha = clamp(properties.getEwmaAlpha(), 0.01, 1.0);
        double observedLatency = Math.max(0L, latencyMs);

        if (profile.getEwmaLatencyMs() == null || profile.getSuccessCount() + profile.getFailureCount() == 0) {
            profile.setEwmaLatencyMs(observedLatency);
        } else {
            profile.setEwmaLatencyMs(profile.getEwmaLatencyMs() * (1.0 - alpha) + observedLatency * alpha);
        }

        profile.setSuccessCount(profile.getSuccessCount() + 1);
        profile.setConsecutiveFailures(0);
        profile.setLastSuccessAt(now);
        profile.setLastObservedAt(now);
        refreshDerivedHealth(profile);
        profileRepository.save(profile);
    }

    @Override
    @Transactional
    public void recordFailure(RoutingCandidate candidate, String failureCategory) {
        if (!properties.isEnabled() || candidate == null) return;

        LocalDateTime now = LocalDateTime.now();
        RoutingHealthProfile profile = getOrCreate(candidate);
        profile.setFailureCount(profile.getFailureCount() + 1);
        profile.setConsecutiveFailures(profile.getConsecutiveFailures() + 1);
        profile.setLastFailureAt(now);
        profile.setLastObservedAt(now);
        refreshDerivedHealth(profile);
        profileRepository.save(profile);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isHealthyForRouting(RoutingCandidate candidate) {
        if (!properties.isEnabled() || !properties.isRejectUnhealthy()) return true;
        RoutingHealthSnapshot snapshot = snapshot(candidate);
        return snapshot == null
                || snapshot.status() != RoutingHealthStatus.UNHEALTHY
                || !snapshot.fresh();
    }

    private RoutingHealthProfile getOrCreate(RoutingCandidate candidate) {
        return profileRepository.findByProviderAndModel(candidate.provider(), candidate.model())
                .orElseGet(() -> RoutingHealthProfile.builder()
                        .provider(candidate.provider())
                        .model(candidate.model())
                        .healthStatus(RoutingHealthStatus.UNKNOWN)
                        .availability(1.0)
                        .successCount(0)
                        .failureCount(0)
                        .consecutiveFailures(0)
                        .build());
    }

    private void refreshDerivedHealth(RoutingHealthProfile profile) {
        long observations = profile.getSuccessCount() + profile.getFailureCount();
        double availability = observations == 0
                ? 1.0
                : (double) profile.getSuccessCount() / observations;
        profile.setAvailability(clamp(availability, 0.0, 1.0));

        List<RoutingOutcome> outcomes = outcomeRepository
                .findTop100ByProviderAndModelOrderByCreatedAtDesc(profile.getProvider(), profile.getModel());

        List<Long> latencies = outcomes.stream()
                .filter(o -> o.getLatencyMs() != null && o.getLatencyMs() >= 0)
                .map(RoutingOutcome::getLatencyMs)
                .sorted()
                .toList();

        if (!latencies.isEmpty()) {
            int index = (int) Math.ceil(0.95 * latencies.size()) - 1;
            profile.setP95LatencyMs(latencies.get(Math.max(0, index)).doubleValue());
        } else if (profile.getEwmaLatencyMs() != null) {
            profile.setP95LatencyMs(profile.getEwmaLatencyMs());
        }

        if (observations < properties.getMinObservations()) {
            profile.setHealthStatus(RoutingHealthStatus.UNKNOWN);
        } else if (profile.getConsecutiveFailures() >= properties.getConsecutiveFailureThreshold()
                || profile.getAvailability() < properties.getUnhealthyAvailability()) {
            profile.setHealthStatus(RoutingHealthStatus.UNHEALTHY);
        } else if (profile.getAvailability() < properties.getDegradedAvailability()) {
            profile.setHealthStatus(RoutingHealthStatus.DEGRADED);
        } else {
            profile.setHealthStatus(RoutingHealthStatus.HEALTHY);
        }
    }

    private RoutingHealthSnapshot toSnapshot(RoutingHealthProfile profile) {
        boolean fresh = profile.getLastObservedAt() != null
                && Duration.between(profile.getLastObservedAt(), LocalDateTime.now()).getSeconds()
                <= properties.getSignalTtlSeconds();
        return new RoutingHealthSnapshot(
                profile.getProvider(),
                profile.getModel(),
                profile.getHealthStatus(),
                profile.getSuccessCount(),
                profile.getFailureCount(),
                profile.getConsecutiveFailures(),
                profile.getAvailability(),
                profile.getEwmaLatencyMs() == null ? 0.0 : profile.getEwmaLatencyMs(),
                profile.getP95LatencyMs() == null ? 0.0 : profile.getP95LatencyMs(),
                profile.getLastObservedAt(),
                fresh);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
