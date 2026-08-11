package com.ai.gateway.quota.service;

import com.ai.gateway.quota.dto.TenantQuotaUsageResponse;

import java.util.UUID;

public interface QuotaService {

    void consumeRequest(
            UUID tenantId);

    void consumeTokens(
            UUID tenantId,
            long tokens);

    TenantQuotaUsageResponse getUsage(
            UUID tenantId);

}