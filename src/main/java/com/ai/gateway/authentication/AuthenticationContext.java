package com.ai.gateway.authentication;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.tenant.TenantType;
import lombok.*;

import java.util.UUID;

@Getter
@Builder
@AllArgsConstructor
public class AuthenticationContext {

    private final AuthenticationType authenticationType;

    private final UUID apiKeyId;

    private final String clientName;

    private final UUID tenantId;

    private final String tenantCode;

    private final String tenantName;

    private final TenantType tenantType;

    private final Provider defaultProvider;

    private final String defaultModel;

    private final String schemaName;

}
