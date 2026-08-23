package com.ai.gateway.provisioning;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.SQLException;

@Service
@RequiredArgsConstructor
public class TenantSchemaCreationService {

    private final DataSource dataSource;

    /**
     * Creates the tenant schema on an independent auto-commit connection.
     *
     * <p>This method intentionally does not use Spring's transaction manager.
     * Tenant schema migration runs while the EntityManagerFactory is being
     * initialized, and making schema creation depend on the transactionManager
     * would create a circular dependency:
     *
     * <pre>
     * entityManagerFactory
     *   -> tenantSchemaMigrationStartupInitializer
     *   -> schema creation
     *   -> transactionManager
     *   -> entityManagerFactory
     * </pre>
     *
     * <p>The schema must also be committed before Flyway obtains its own
     * connection, so the DDL is executed with auto-commit enabled.</p>
     */
    public void createSchema(String schemaName) {

        String quoted =
                "\"" + schemaName.replace("\"", "\"\"") + "\"";

        try (var connection = dataSource.getConnection()) {

            boolean previousAutoCommit =
                    connection.getAutoCommit();

            try {
                connection.setAutoCommit(true);

                try (var statement = connection.createStatement()) {
                    statement.execute(
                            "CREATE SCHEMA IF NOT EXISTS " + quoted);
                }

            } finally {
                if (!previousAutoCommit) {
                    connection.setAutoCommit(false);
                }
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to create tenant schema: " + schemaName,
                    e);
        }
    }
}
