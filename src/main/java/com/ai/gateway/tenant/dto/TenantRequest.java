package com.ai.gateway.tenant.dto;

import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.tenant.TenantType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantRequest {

    @NotBlank
    private String tenantCode;

    @NotBlank
    private String tenantName;

    @NotNull
    private Plan plan;

    @NotNull
    private TenantType type;

    @NotNull
    private Provider defaultProvider;

    @NotBlank
    private String defaultModel;
}
