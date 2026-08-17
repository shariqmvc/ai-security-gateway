package com.ai.gateway.governance.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.cost.service.CostService;
import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.quota.service.QuotaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Synchronous, transactional post-provider governance boundary.
 *
 * Quota consumption and budget enforcement remain on the response critical
 * path, but share one transaction so tenant schema routing and transaction
 * setup are performed once. Persistence/analytics remain asynchronous.
 */
@Service
@RequiredArgsConstructor
public class GovernanceGuardrailService {

    private final QuotaService quotaService;
    private final CostService costService;

    @Transactional
    public void enforce(
            UUID requestId,
            AuthenticationContext auth,
            AIRequest request,
            AIResponse response) {

        if (response == null || response.getUsage() == null) {
            return;
        }

        long totalTokens = response.getUsage().getTotalTokens();
        if (totalTokens > 0) {
            quotaService.consumeTokens(
                    auth.getTenantId(),
                    totalTokens);
        }

        costService.enforceBudget(
                requestId,
                auth,
                request,
                response);
    }
}
