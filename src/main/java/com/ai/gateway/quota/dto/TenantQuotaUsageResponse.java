package com.ai.gateway.quota.dto;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantQuotaUsageResponse {

    private UUID tenantId;

    private QuotaUsageDto daily;

    private QuotaUsageDto monthlyTokens;
}
