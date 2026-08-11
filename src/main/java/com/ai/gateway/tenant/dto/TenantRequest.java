package com.ai.gateway.tenant.dto;

import com.ai.gateway.entitlement.enums.Plan;
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
}
