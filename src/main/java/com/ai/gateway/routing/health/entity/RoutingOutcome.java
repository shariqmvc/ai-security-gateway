package com.ai.gateway.routing.health.entity;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.RoutingStrategy;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ROUTING_OUTCOME",
        indexes = {
                @Index(name = "idx_routing_outcome_provider_model", columnList = "provider,model"),
                @Index(name = "idx_routing_outcome_created_at", columnList = "created_at"),
                @Index(name = "idx_routing_outcome_tenant", columnList = "tenant_id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoutingOutcome {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "request_id", nullable = false, unique = true)
    private UUID requestId;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 64)
    private Provider provider;

    @Column(nullable = false, length = 255)
    private String model;

    @Enumerated(EnumType.STRING)
    @Column(name = "routing_strategy", length = 64)
    private RoutingStrategy routingStrategy;

    @Column(name = "selected_score")
    private Double selectedScore;

    @Column(name = "selected_rank")
    private Integer selectedRank;

    @Column(name = "candidate_count")
    private Integer candidateCount;

    @Column(name = "selection_reason", length = 128)
    private String selectionReason;

    @Column(name = "routing_priority", length = 64)
    private String routingPriority;

    @Column(name = "extensive_research", nullable = false)
    private boolean extensiveResearch;

    @Column(name = "execution_role", length = 255)
    private String executionRole;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "failure_category", length = 128)
    private String failureCategory;

    @Column(name = "latency_ms")
    private Long latencyMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
