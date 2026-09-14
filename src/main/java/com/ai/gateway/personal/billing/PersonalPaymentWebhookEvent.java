package com.ai.gateway.personal.billing;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="PERSONAL_PAYMENT_WEBHOOK_EVENTS")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PersonalPaymentWebhookEvent {
 @Id @GeneratedValue private UUID id;
 @Column(name="provider", nullable=false, length=64) private String provider;
 @Column(name="event_id", nullable=false, unique=true, length=255) private String eventId;
 @Column(name="event_type", nullable=false, length=64) private String eventType;
 @Column(name="received_at", nullable=false) private LocalDateTime receivedAt;
 @Column(name="processed_at") private LocalDateTime processedAt;
 @Column(name="status", nullable=false, length=32) private String status;
 @Column(name="failure_reason", length=1000) private String failureReason;
}
