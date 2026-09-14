package com.ai.gateway.personal.usage.dto;
import lombok.*;
import java.math.BigDecimal;
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PersonalUsageSummaryResponse {
 private long requestsToday; private long successfulRequestsToday; private long failedRequestsToday;
 private long requestsThisMonth; private long inputTokensThisMonth; private long outputTokensThisMonth;
 private long totalTokensThisMonth; private BigDecimal costThisMonth; private long cacheHitsThisMonth;
 private long contextTokensSavedThisMonth; private long quotaRequestsRemainingToday; private long quotaTokensRemainingThisMonth;
}
