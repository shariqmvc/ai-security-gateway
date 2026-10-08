package com.ai.gateway.personal.credit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "PERSONAL_CREDIT_RESERVATIONS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalCreditReservation {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "personal_account_id", nullable = false)
    private UUID personalAccountId;

    @Column(name = "reserved_amount", nullable = false, precision = 19, scale = 8)
    private BigDecimal reservedAmount;

    @Column(name = "captured_amount", nullable = false, precision = 19, scale = 8)
    @Builder.Default
    private BigDecimal capturedAmount = BigDecimal.ZERO;

    @Column(name = "settlement_amount", precision = 19, scale = 8)
    private BigDecimal settlementAmount;

    @Column(name = "provider_invocation_started", nullable = false)
    @Builder.Default
    private boolean providerInvocationStarted = false;

    @Column(name = "settlement_provider", length = 32)
    private String settlementProvider;

    @Column(name = "settlement_model", length = 255)
    private String settlementModel;

    @Column(name = "settlement_input_tokens")
    private Integer settlementInputTokens;

    @Column(name = "settlement_output_tokens")
    private Integer settlementOutputTokens;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private PersonalCreditReservationStatus status = PersonalCreditReservationStatus.RESERVED;

    @Column(name = "reference_id", nullable = false, unique = true, length = 128)
    private String referenceId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
