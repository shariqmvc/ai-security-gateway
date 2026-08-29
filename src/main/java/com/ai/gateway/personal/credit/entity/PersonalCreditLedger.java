package com.ai.gateway.personal.credit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "PERSONAL_CREDIT_LEDGER")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalCreditLedger {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "personal_account_id", nullable = false)
    private UUID personalAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 32)
    private PersonalCreditLedgerEntryType entryType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 8)
    private BigDecimal amount;

    @Column(name = "reference_id", unique = true, length = 128)
    private String referenceId;

    @Column(name = "reservation_id")
    private UUID reservationId;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
