package com.ai.gateway.personal.usage.dto;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.personal.usage.entity.PersonalRequestStatus;
import com.ai.gateway.personal.usage.entity.PersonalResponseRating;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PersonalRequestHistoryResponse {
 private UUID requestId; private PersonalRequestStatus status; private Provider provider; private String model;
 private String billingMode; private String routingStrategy; private String maskedPrompt; private String maskedResponse;
 private Integer inputTokens; private Integer outputTokens; private Integer totalTokens;
 private Integer estimatedInputTokens; private Integer estimatedOptimizedTokens; private Integer estimatedTokensSaved;
 private Integer contextWindowTokens; private Long latencyMs; private Long providerLatencyMs; private BigDecimal cost;
 private boolean cacheHit; private boolean ragEnabled; private String errorCategory; private LocalDateTime createdAt; private PersonalResponseRating responseRating;
}
