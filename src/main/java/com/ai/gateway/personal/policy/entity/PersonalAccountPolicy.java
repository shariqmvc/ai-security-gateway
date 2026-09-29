package com.ai.gateway.personal.policy.entity;

import com.ai.gateway.personal.entity.PersonalAccount;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "PERSONAL_ACCOUNT_POLICIES")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalAccountPolicy {

    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "personal_account_id", nullable = false, unique = true)
    private PersonalAccount personalAccount;

    @Column(name = "quota_enabled", nullable = false)
    @Builder.Default
    private boolean quotaEnabled = true;

    @Column(name = "requests_per_minute", nullable = false)
    @Builder.Default
    private long requestsPerMinute = 0;

    @Column(name = "requests_per_day", nullable = false)
    @Builder.Default
    private long requestsPerDay = 0;

    @Column(name = "monthly_token_quota", nullable = false)
    @Builder.Default
    private long monthlyTokenQuota = 0;

    @Column(name = "max_input_tokens", nullable = false)
    @Builder.Default
    private long maxInputTokens = 0;

    @Column(name = "max_output_tokens", nullable = false)
    @Builder.Default
    private long maxOutputTokens = 0;

    @Column(name = "max_concurrent_requests", nullable = false)
    @Builder.Default
    private long maxConcurrentRequests = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
