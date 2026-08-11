package com.ai.gateway.quota;

import com.ai.gateway.enums.QuotaPeriodType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "TENANT_QUOTA_USAGE",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_TENANT_QUOTA_PERIOD",
                        columnNames = {
                                "tenant_id",
                                "period_type",
                                "period_start"
                        })
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantQuotaUsage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(
            name = "tenant_id",
            nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "period_type",
            nullable = false)
    private QuotaPeriodType periodType;

    @Column(
            name = "period_start",
            nullable = false)
    private LocalDate periodStart;

    @Column(
            name = "request_count",
            nullable = false)
    @Builder.Default
    private Long requestCount = 0L;

    @Column(
            name = "token_count",
            nullable = false)
    @Builder.Default
    private Long tokenCount = 0L;

    @Version
    private Long version;
}