package com.ai.gateway.tenant.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TenantRequest {

    private String tenantCode;

    private String tenantName;

}
