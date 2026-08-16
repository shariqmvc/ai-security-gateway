package com.ai.gateway.service;

import com.ai.gateway.entity.ApiKey;
import com.ai.gateway.enums.ApiKeyStatus;
import com.ai.gateway.security.*;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceImplTest {

    @Mock ApiKeyRepository repository;
    @InjectMocks ApiKeyServiceImpl service;

    @Test
    void shouldProvisionInitialKey() {
        Tenant tenant = activeTenant();
        when(repository.findFirstByTenantIdAndStatusOrderByCreatedAtAsc(
                tenant.getId(), ApiKeyStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(repository.findByApiKeyWithTenant(anyString()))
                .thenReturn(Optional.empty());
        when(repository.save(any(ApiKey.class)))
                .thenAnswer(invocation -> {
                    ApiKey key = invocation.getArgument(0);
                    key.setId(UUID.randomUUID());
                    return key;
                });

        ApiKeyProvisioningResult result = service.provisionInitialKey(tenant);

        assertNotNull(result.apiKey());
        assertTrue(result.apiKey().startsWith("aegis_"));
        assertEquals(tenant.getId(), result.tenantId());
        verify(repository).save(any(ApiKey.class));
    }

    @Test
    void shouldBeIdempotentWhenActiveKeyExists() {
        Tenant tenant = activeTenant();
        ApiKey existing = ApiKey.builder()
                .id(UUID.randomUUID())
                .apiKey("aegis_existing")
                .clientName("bootstrap")
                .tenant(tenant)
                .status(ApiKeyStatus.ACTIVE)
                .build();
        when(repository.findFirstByTenantIdAndStatusOrderByCreatedAtAsc(
                tenant.getId(), ApiKeyStatus.ACTIVE))
                .thenReturn(Optional.of(existing));

        ApiKeyProvisioningResult result = service.provisionInitialKey(tenant);

        assertNull(result.apiKey());
        verify(repository, never()).save(any(ApiKey.class));
    }

    @Test
    void shouldRotateActiveKeys() {
        Tenant tenant = activeTenant();
        ApiKey existing = ApiKey.builder()
                .id(UUID.randomUUID())
                .apiKey("aegis_existing")
                .clientName("bootstrap")
                .tenant(tenant)
                .status(ApiKeyStatus.ACTIVE)
                .build();
        when(repository.findByTenantIdAndStatusForUpdate(
                tenant.getId(), ApiKeyStatus.ACTIVE))
                .thenReturn(List.of(existing));
        when(repository.findByApiKeyWithTenant(anyString())).thenReturn(Optional.empty());
        when(repository.save(any(ApiKey.class)))
                .thenAnswer(invocation -> {
                    ApiKey key = invocation.getArgument(0);
                    if (key.getId() == null) key.setId(UUID.randomUUID());
                    return key;
                });

        ApiKeyProvisioningResult result = service.rotate(tenant, "rotated-client");

        assertEquals(ApiKeyStatus.INACTIVE, existing.getStatus());
        assertEquals("rotated-client", result.clientName());
        assertNotNull(result.apiKey());
        verify(repository).saveAll(List.of(existing));
    }

    @Test
    void shouldRejectRotationForNonActiveTenant() {
        Tenant tenant = activeTenant();
        tenant.setStatus(TenantStatus.SUSPENDED);

        assertThrows(IllegalStateException.class,
                () -> service.rotate(tenant, "client"));

        verifyNoInteractions(repository);
    }

    @Test
    void shouldAuthenticateOnlyForActiveTenantAndKey() {
        Tenant tenant = activeTenant();
        ApiKey key = ApiKey.builder()
                .apiKey("aegis_test")
                .clientName("test")
                .tenant(tenant)
                .status(ApiKeyStatus.ACTIVE)
                .build();
        when(repository.findByApiKeyWithTenant("aegis_test"))
                .thenReturn(Optional.of(key));

        assertTrue(service.authenticate("aegis_test").isPresent());

        tenant.setStatus(TenantStatus.SUSPENDED);
        assertTrue(service.authenticate("aegis_test").isEmpty());
    }

    @Test
    void shouldRevokeOnlyKeyOwnedByTenant() {
        Tenant tenant = activeTenant();
        UUID keyId = UUID.randomUUID();
        ApiKey key = ApiKey.builder()
                .id(keyId)
                .apiKey("aegis_test")
                .clientName("test")
                .tenant(tenant)
                .status(ApiKeyStatus.ACTIVE)
                .build();
        when(repository.findById(keyId)).thenReturn(Optional.of(key));

        service.revoke(tenant, keyId);

        assertEquals(ApiKeyStatus.INACTIVE, key.getStatus());
        verify(repository).save(key);
    }

    private Tenant activeTenant() {
        return Tenant.builder()
                .id(UUID.randomUUID())
                .tenantCode("TEST")
                .tenantName("Test")
                .status(TenantStatus.ACTIVE)
                .build();
    }
}
