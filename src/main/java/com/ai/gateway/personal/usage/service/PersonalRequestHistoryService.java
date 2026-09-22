package com.ai.gateway.personal.usage.service;
import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.*;
import com.ai.gateway.personal.usage.dto.*;
import org.springframework.data.domain.*;
import java.util.UUID;
public interface PersonalRequestHistoryService {
 void recordSuccess(UUID requestId, AuthenticationContext auth, AIRequest request, AIResponse response,
   String maskedPrompt,long latencyMs,long providerLatencyMs,Integer estimatedInputTokens,
   Integer estimatedOptimizedTokens,Integer estimatedTokensSaved,Integer contextWindowTokens,
   boolean cacheHit,boolean ragEnabled);
 void recordFailure(UUID requestId,AuthenticationContext auth,AIRequest request,String maskedPrompt,
   long latencyMs,long providerLatencyMs,String errorCategory);
 void recordBlocked(UUID requestId,AuthenticationContext auth,AIRequest request,String maskedPrompt,
   long latencyMs,long providerLatencyMs,String errorCategory);
 Page<PersonalRequestHistoryResponse> list(UUID accountId,Pageable pageable);
 PersonalRequestHistoryResponse get(UUID accountId,UUID requestId);
 PersonalUsageSummaryResponse summary(UUID accountId);
}
