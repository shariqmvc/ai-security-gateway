package com.ai.gateway.health;

import com.ai.gateway.enums.HealthStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class HealthService {

    private final List<HealthIndicator> indicators;

    public GatewayHealthResponse checkHealth() {

        List<HealthResult> results = indicators.stream()
                .map(HealthIndicator::check)
                .toList();

        HealthStatus overallStatus = results.stream()
                .anyMatch(r -> r.getStatus() == HealthStatus.DOWN)
                ? HealthStatus.DOWN
                : HealthStatus.UP;

        return GatewayHealthResponse.builder()
                .overallStatus(overallStatus)
                .timestamp(LocalDateTime.now())
                .components(results)
                .build();
    }

}