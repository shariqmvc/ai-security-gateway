package com.ai.gateway.routing.health.entity;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.health.RoutingHealthStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ROUTING_HEALTH_PROFILE",
        uniqueConstraints = @UniqueConstraint(name = "uk_routing_health_provider_model",
                columnNames = {"provider", "model"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutingHealthProfile {

    @Id
    @GeneratedValue
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private Provider provider;

    @Column(nullable = false, length = 255)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "health_status", nullable = false, length = 32)
    private RoutingHealthStatus healthStatus;

    @Column(name = "success_count", nullable = false)
    private long successCount;

    @Column(name = "failure_count", nullable = false)
    private long failureCount;

    @Column(name = "consecutive_failures", nullable = false)
    private long consecutiveFailures;

    @Column(name = "ewma_latency_ms")
    private Double ewmaLatencyMs;

    @Column(name = "p95_latency_ms")
    private Double p95LatencyMs;

    @Column(nullable = false)
    private double availability;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @Column(name = "last_failure_at")
    private LocalDateTime lastFailureAt;

    @Column(name = "last_observed_at")
    private LocalDateTime lastObservedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (healthStatus == null) healthStatus = RoutingHealthStatus.UNKNOWN;
        updatedAt = LocalDateTime.now();
        if (lastObservedAt == null) lastObservedAt = updatedAt;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
