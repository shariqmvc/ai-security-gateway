package com.ai.gateway.budget.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "TENANT_BUDGET_USAGE",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_tenant_budget_period",
                columnNames = {
                        "tenant_id",
                        "period_start"
                }))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantBudgetUsage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(
            name = "tenant_id",
            nullable = false)
    private UUID tenantId;

    @Column(
            name = "period_start",
            nullable = false)
    private LocalDate periodStart;

    @Column(
            name = "amount_used",
            nullable = false,
            precision = 19,
            scale = 8)
    private BigDecimal amountUsed;

    @Version
    private Long version;
}
