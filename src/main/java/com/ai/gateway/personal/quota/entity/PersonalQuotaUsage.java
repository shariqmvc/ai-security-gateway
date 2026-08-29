package com.ai.gateway.personal.quota.entity;

import com.ai.gateway.enums.QuotaPeriodType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(
        name = "PERSONAL_QUOTA_USAGE",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_PERSONAL_QUOTA_PERIOD",
                        columnNames = {
                                "personal_account_id",
                                "period_type",
                                "period_start"
                        })
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalQuotaUsage {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "personal_account_id", nullable = false)
    private UUID personalAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 32)
    private QuotaPeriodType periodType;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "request_count", nullable = false)
    @Builder.Default
    private Long requestCount = 0L;

    @Column(name = "token_count", nullable = false)
    @Builder.Default
    private Long tokenCount = 0L;

    @Version
    private Long version;
}
