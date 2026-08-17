package com.ai.gateway.service;

import com.ai.gateway.tenant.TenantAccessDeniedException;
import com.ai.gateway.tenant.TenantContext;
import com.ai.gateway.tenant.TenantRepository;
import com.ai.gateway.tenant.TenantSchemaContext;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import com.ai.gateway.provisioning.TenantSchemaNameResolver;
import com.ai.gateway.tenant.Tenant;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class TenantOperationalContextSafetyTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final TenantRepository tenantRepository = mock(TenantRepository.class);

    private final TenantSchemaRoutingService service =
            new TenantSchemaRoutingService(
                    entityManager,
                    tenantRepository,
                    new TenantSchemaNameResolver());

    @AfterEach
    void cleanup() {
        TenantSchemaContext.clear();
        TenantContext.clear();
    }


    @Test
    void shouldNotLeakAppliedSchemaMarkerAcrossTransactions() {
        UUID tenantId = UUID.randomUUID();
        String schema = expectedSchema(tenantId);

        TenantContext.set(tenantId);
        TenantSchemaContext.set(tenantId, schema);

        jakarta.persistence.Query query = mock(jakarta.persistence.Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.useTenantSchema(tenantId);
            verify(entityManager, times(1)).createNativeQuery(anyString());

            for (TransactionSynchronization synchronization :
                    TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_COMMITTED);
            }
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.useTenantSchema(tenantId);
            verify(entityManager, times(2)).createNativeQuery(anyString());
        } finally {
            for (TransactionSynchronization synchronization :
                    TransactionSynchronizationManager.getSynchronizations()) {
                synchronization.afterCompletion(
                        TransactionSynchronization.STATUS_COMMITTED);
            }
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void shouldRejectSchemaContextWithoutTenantIdentity() {
        TenantSchemaContext.set("tenant_" + UUID.randomUUID()
                .toString().replace("-", ""));

        assertThrows(
                IllegalStateException.class,
                () -> service.useTenantSchema(UUID.randomUUID()));

        verifyNoInteractions(tenantRepository);
        verifyNoInteractions(entityManager);
    }

    @Test
    void shouldRejectAuthenticatedTenantWithInvalidSchemaContext() {
        UUID tenantId = UUID.randomUUID();
        TenantContext.set(tenantId);
        TenantSchemaContext.set("tenant_wrong");

        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .schemaName(expectedSchema(tenantId))
                .build();

        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(tenant));

        assertThrows(
                IllegalStateException.class,
                () -> service.useTenantSchema(tenantId));

        verify(entityManager, never()).createNativeQuery(anyString());
    }

    @Test
    void shouldRejectAuthenticatedTenantRoutingToDifferentTenant() {
        UUID authenticated = UUID.randomUUID();
        UUID requested = UUID.randomUUID();

        TenantContext.set(authenticated);

        assertThrows(
                TenantAccessDeniedException.class,
                () -> service.useTenantSchema(requested));

        verifyNoInteractions(tenantRepository);
        verifyNoInteractions(entityManager);
    }

    @Test
    void shouldRejectPersistedSchemaPointingToAnotherTenant() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .id(tenantId)
                .schemaName("tenant_other")
                .build();

        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(tenant));

        assertThrows(
                IllegalStateException.class,
                () -> service.useTenantSchema(tenantId));

        verifyNoInteractions(entityManager);
    }

    private String expectedSchema(UUID tenantId) {
        return "tenant_" + tenantId.toString()
                .replace("-", "")
                .toLowerCase();
    }
}
