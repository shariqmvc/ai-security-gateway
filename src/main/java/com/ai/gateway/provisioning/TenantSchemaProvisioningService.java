package com.ai.gateway.provisioning;

import com.ai.gateway.tenant.Tenant;
import lombok.RequiredArgsConstructor;
import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
@RequiredArgsConstructor
public class TenantSchemaProvisioningService {

    private final TenantSchemaNameResolver schemaNameResolver;
    private final TenantSchemaCreationService schemaCreationService;
    private final DataSource dataSource;

    public String provision(Tenant tenant) {

        String schemaName = tenant.getSchemaName();

        if (schemaName == null || schemaName.isBlank()) {
            schemaName = schemaNameResolver.resolve(tenant);
            tenant.setSchemaName(schemaName);
        }

        // Must commit before Flyway uses another connection.
        schemaCreationService.createSchema(schemaName);

        migrateSchema(schemaName);

        return schemaName;
    }

    public void migrateSchema(String schemaName) {

        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException(
                    "Tenant schema name must not be null or blank.");
        }

        if (!schemaName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException(
                    "Invalid tenant schema name: " + schemaName);
        }

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .createSchemas(false)
                .locations("classpath:db/tenant-migration")
                .load();

        flyway.migrate();
    }
}