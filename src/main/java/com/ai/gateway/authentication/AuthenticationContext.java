package com.ai.gateway.authentication;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.security.SecurityRole;
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

    private final UUID personalUserId;
    private final UUID personalAccountId;

    private final UUID tenantId;
    private final String tenantCode;
    private final String tenantName;
    private final TenantType tenantType;

    private final Provider defaultProvider;
    private final String defaultModel;
    private final String schemaName;

    /**
     * Platform principals have no tenant by default.
     * Their role authorizes platform operations only; tenant data access
     * requires an explicit tenant-scoped delegation in the future.
     */
    @Builder.Default
    private final SecurityRole role = SecurityRole.TENANT_USER;

    @Builder.Default
    private final boolean platformPrincipal = false;

    @Builder.Default
    private final boolean personalPrincipal = false;
}
