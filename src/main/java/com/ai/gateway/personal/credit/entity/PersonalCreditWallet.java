package com.ai.gateway.personal.credit.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "PERSONAL_CREDIT_WALLETS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalCreditWallet {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "personal_account_id", nullable = false, unique = true)
    private UUID personalAccountId;

    @Column(name = "balance", nullable = false, precision = 19, scale = 8)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "reserved_balance", nullable = false, precision = 19, scale = 8)
    @Builder.Default
    private BigDecimal reservedBalance = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    @Transient
    public BigDecimal getAvailableBalance() {
        return balance.subtract(reservedBalance);
    }
}
