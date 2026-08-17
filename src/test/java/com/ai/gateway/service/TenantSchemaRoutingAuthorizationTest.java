package com.ai.gateway.service;

import com.ai.gateway.tenant.TenantAccessDeniedException;
import com.ai.gateway.tenant.TenantContext;
import com.ai.gateway.tenant.TenantRepository;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import com.ai.gateway.provisioning.TenantSchemaNameResolver;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantSchemaRoutingAuthorizationTest {

    private final EntityManager entityManager =
            mock(EntityManager.class);

    private final TenantRepository tenantRepository =
            mock(TenantRepository.class);

    private final TenantSchemaRoutingService service =
            new TenantSchemaRoutingService(
                    entityManager,
                    tenantRepository,
                    new TenantSchemaNameResolver());

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }


    @Test
    void shouldRejectTenantWhosePersistedSchemaDoesNotMatchItsIdentity() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);

        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .schemaName("tenant_other")
                .build();

        when(tenantRepository.findById(tenantId))
                .thenReturn(java.util.Optional.of(tenant));

        assertThrows(
                IllegalStateException.class,
                () -> service.useTenantSchema(tenantId));
    }

    @Test
    void shouldRejectRoutingAuthenticatedRequestToAnotherTenant() {
        UUID authenticatedTenantId = UUID.randomUUID();
        UUID requestedTenantId = UUID.randomUUID();

        TenantContext.set(authenticatedTenantId);

        assertThrows(
                TenantAccessDeniedException.class,
                () -> service.useTenantSchema(requestedTenantId));
    }
}
