package com.ai.gateway.governance.service;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.business.cost.service.CostService;
import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.contract.AIResponse;
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

        if (auth != null && auth.isPersonalPrincipal()) {
            // Business quota/budget services are tenant-schema scoped. A
            // Personal principal deliberately has no tenantId, so never route
            // Personal execution through those services. Personal CREDIT is
            // reconciled by PersonalCreditExecutionService before this guard.
            return;
        }

        if (auth == null || auth.getTenantId() == null) {
            throw new IllegalStateException(
                    "Authenticated tenant context is required for Business governance.");
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
