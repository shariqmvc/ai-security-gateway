package com.ai.gateway.entitlement.entity;

import com.ai.gateway.entitlement.enums.Feature;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "tenant_entitlements",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tenant_entitlement_tenant",
                        columnNames = "tenant_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "tenant_entitlement_features",
            joinColumns = @JoinColumn(name = "entitlement_id"),
            uniqueConstraints = {
                    @UniqueConstraint(
                            name = "uk_entitlement_feature",
                            columnNames = {"entitlement_id", "feature"}
                    )
            }
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false, length = 100)
    @Builder.Default
    private Set<Feature> features = new HashSet<>();

    @Column(name = "requests_per_minute", nullable = false)
    private Long requestsPerMinute;

    @Column(name = "requests_per_day", nullable = false)
    private Long requestsPerDay;

    @Column(name = "monthly_token_quota", nullable = false)
    private Long monthlyTokenQuota;

    @Column(
            name = "monthly_budget",
            nullable = false,
            precision = 19,
            scale = 8
    )
    private BigDecimal monthlyBudget;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    private boolean enabled;



    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
