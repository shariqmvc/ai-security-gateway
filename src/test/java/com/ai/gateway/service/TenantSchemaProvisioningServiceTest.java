package com.ai.gateway.service;

import com.ai.gateway.provisioning.TenantSchemaNameResolver;
import com.ai.gateway.provisioning.TenantSchemaProvisioningService;
import com.ai.gateway.tenant.Tenant;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantSchemaProvisioningServiceTest {

    private final TenantSchemaNameResolver resolver =
            new TenantSchemaNameResolver();

    @Test
    void shouldResolveDeterministicSchemaNameFromTenantId() {

        UUID id = UUID.fromString(
                "06f47207-fa05-4dfc-af77-c9150417a26c");

        Tenant tenant = Tenant.builder()
                .id(id)
                .build();

        String schema = resolver.resolve(tenant);

        assertEquals(
                "tenant_06f47207fa054dfcaf77c9150417a26c",
                schema);
    }

    @Test
    void shouldRejectTenantWithoutId() {

        Tenant tenant = Tenant.builder().build();

        assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolve(tenant));
    }

    @Test
    void shouldProducePostgresSafeSchemaName() {

        Tenant tenant = Tenant.builder()
                .id(UUID.randomUUID())
                .build();

        String schema = resolver.resolve(tenant);

        assertTrue(schema.startsWith("tenant_"));
        assertTrue(schema.length() <= 63);
        assertTrue(schema.matches("[a-z0-9_]+"));
    }
}
