package com.ai.gateway.personal.billing;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="PERSONAL_BILLING_SETTINGS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PersonalBillingSettings {
 @Id @GeneratedValue private UUID id;
 @Column(name="personal_account_id",nullable=false,unique=true) private UUID personalAccountId;
 @Column(name="low_balance_alert_enabled",nullable=false) @Builder.Default private boolean lowBalanceAlertEnabled=true;
 @Column(name="low_balance_threshold",nullable=false,precision=19,scale=8) @Builder.Default private BigDecimal lowBalanceThreshold=BigDecimal.valueOf(2);
 @Column(name="auto_top_up_enabled",nullable=false) @Builder.Default private boolean autoTopUpEnabled=false;
 @Column(name="auto_top_up_amount",nullable=false,precision=19,scale=8) @Builder.Default private BigDecimal autoTopUpAmount=BigDecimal.TEN;
 @Column(name="monthly_spend_cap",precision=19,scale=8) private BigDecimal monthlySpendCap;
 @Column(name="updated_at",nullable=false) private LocalDateTime updatedAt;
 @Column(name="last_low_balance_alert_at") private LocalDateTime lastLowBalanceAlertAt;
}