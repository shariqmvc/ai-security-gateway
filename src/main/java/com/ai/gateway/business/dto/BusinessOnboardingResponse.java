package com.ai.gateway.business.dto;

import com.ai.gateway.business.BusinessStatus;
import com.ai.gateway.business.BusinessType;
import com.ai.gateway.tenant.TenantStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessOnboardingResponse {
    private UUID businessId;
    private UUID tenantId;
    private String tenantCode;
    private String businessName;
    private BusinessType businessType;
    private BusinessStatus businessStatus;
    private TenantStatus tenantStatus;
    private String schemaName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
