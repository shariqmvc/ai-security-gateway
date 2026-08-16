package com.ai.gateway.service;

import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.provisioning.*;
import com.ai.gateway.security.ApiKeyService;
import com.ai.gateway.tenant.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TenantProvisioningServiceTest {

    @Mock TenantRepository tenantRepository;
    @Mock TenantProvisioningExecutionService executionService;
    @Mock TenantProvisioningFailureService failureService;

    @InjectMocks TenantProvisioningServiceImpl service;

    @Test
    void shouldProvisionRequestedTenant() {
        UUID tenantId = UUID.randomUUID();
        service.provision(tenantId);
        verify(executionService).execute(tenantId);
        verifyNoInteractions(failureService);
    }

    @Test
    void shouldPersistFailureAndRethrow() {
        UUID tenantId = UUID.randomUUID();
        doThrow(new IllegalStateException("boom"))
                .when(executionService).execute(tenantId);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> service.provision(tenantId));

        assertEquals("boom", ex.getMessage());
        verify(failureService).markFailed(tenantId, "boom");
    }

    @Test
    void shouldAllowRetryOnlyFromFailed() {
        UUID tenantId = UUID.randomUUID();
        Tenant failed = tenant(tenantId, TenantStatus.FAILED);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(failed));

        service.retry(tenantId);

        verify(executionService).execute(tenantId);
    }

    @Test
    void shouldRejectRetryFromActive() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(tenant(tenantId, TenantStatus.ACTIVE)));

        assertDoesNotThrow(() -> service.retry(tenantId));
        verifyNoInteractions(executionService);
    }

    @Test
    void shouldRejectRetryFromProvisioning() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId))
                .thenReturn(Optional.of(tenant(tenantId, TenantStatus.PROVISIONING)));

        assertThrows(IllegalStateException.class, () -> service.retry(tenantId));
        verifyNoInteractions(executionService);
    }

    @Test
    void shouldReturnProvisioningStatus() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = tenant(tenantId, TenantStatus.FAILED);
        tenant.setProvisioningAttempts(2);
        tenant.setProvisioningFailureReason("provider unavailable");
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        TenantProvisioningStatus status = service.status(tenantId);

        assertEquals(tenantId, status.tenantId());
        assertEquals(TenantStatus.FAILED, status.status());
        assertEquals(2, status.attempts());
        assertEquals("provider unavailable", status.failureReason());
    }

    private Tenant tenant(UUID id, TenantStatus status) {
        return Tenant.builder()
                .id(id)
                .tenantCode("TEST")
                .tenantName("Test")
                .status(status)
                .type(TenantType.STANDARD)
                .plan(Plan.PROFESSIONAL)
                .defaultProvider(Provider.GEMINI)
                .defaultModel("gemini-3.6-flash")
                .build();
    }
}
