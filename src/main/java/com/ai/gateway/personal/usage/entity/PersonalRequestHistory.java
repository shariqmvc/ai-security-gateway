package com.ai.gateway.personal.usage.entity;
import com.ai.gateway.core.model.Provider;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name="PERSONAL_REQUEST_HISTORY", indexes={
 @Index(name="idx_personal_request_account_created", columnList="personal_account_id,created_at"),
 @Index(name="idx_personal_request_account_status", columnList="personal_account_id,status")})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PersonalRequestHistory {
 @Id @GeneratedValue private UUID id;
 @Column(name="request_id",nullable=false,unique=true) private UUID requestId;
 @Column(name="personal_account_id",nullable=false) private UUID personalAccountId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=16) private PersonalRequestStatus status;
 @Enumerated(EnumType.STRING) private Provider provider;
 @Column(length=255) private String model;
 @Column(name="billing_mode",length=32) private String billingMode;
 @Column(name="routing_strategy",length=64) private String routingStrategy;
    @Column(name="masked_prompt", columnDefinition="TEXT")
    private String maskedPrompt;

    @Column(name="masked_response", columnDefinition="TEXT")
    private String maskedResponse;
 @Column(name="input_tokens") private Integer inputTokens;
 @Column(name="output_tokens") private Integer outputTokens;
 @Column(name="total_tokens") private Integer totalTokens;
 @Column(name="estimated_input_tokens") private Integer estimatedInputTokens;
 @Column(name="estimated_optimized_tokens") private Integer estimatedOptimizedTokens;
 @Column(name="estimated_tokens_saved") private Integer estimatedTokensSaved;
 @Column(name="context_window_tokens") private Integer contextWindowTokens;
 @Column(name="latency_ms") private Long latencyMs;
 @Column(name="provider_latency_ms") private Long providerLatencyMs;
 @Column(name="cost",precision=19,scale=8) private BigDecimal cost;
 @Column(name="cache_hit",nullable=false) @Builder.Default private boolean cacheHit=false;
 @Column(name="rag_enabled",nullable=false) @Builder.Default private boolean ragEnabled=false;
 @Column(name="error_category",length=128) private String errorCategory;
 @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
 @PrePersist void prePersist(){if(createdAt==null)createdAt=LocalDateTime.now();}
}
