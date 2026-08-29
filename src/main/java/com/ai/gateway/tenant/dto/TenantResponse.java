package com.ai.gateway.tenant.dto;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.tenant.TenantStatus;
import com.ai.gateway.tenant.TenantType;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantResponse {

    private String tenantCode;

    private String tenantName;

    private TenantStatus status;

    private TenantType type;

    private Provider defaultProvider;

    private String defaultModel;

}
