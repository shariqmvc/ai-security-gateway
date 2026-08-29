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
