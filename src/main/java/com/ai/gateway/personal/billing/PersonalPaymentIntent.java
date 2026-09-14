package com.ai.gateway.personal.billing;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="PERSONAL_PAYMENT_INTENTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PersonalPaymentIntent {
 @Id @GeneratedValue private UUID id;
 @Column(name="personal_account_id", nullable=false) private UUID personalAccountId;
 @Column(name="package_code", nullable=false, length=64) private String packageCode;
 @Column(name="currency", nullable=false, length=3) private String currency;
 @Column(name="amount", nullable=false, precision=19, scale=8) private BigDecimal amount;
 @Column(name="credits", nullable=false, precision=19, scale=8) private BigDecimal credits;
 @Column(name="provider", nullable=false, length=64) private String provider;
 @Column(name="provider_payment_id", length=255) private String providerPaymentId;
 @Enumerated(EnumType.STRING) @Column(name="status", nullable=false, length=32) private PersonalPaymentStatus status;
 @Column(name="idempotency_key", nullable=false, unique=true, length=128) private String idempotencyKey;
 @Column(name="created_at", nullable=false) private LocalDateTime createdAt;
 @Column(name="updated_at", nullable=false) private LocalDateTime updatedAt;
 @Column(name="completed_at") private LocalDateTime completedAt;
}
