package com.ai.gateway.config;

import com.ai.gateway.provisioning.TenantSchemaProvisioningService;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Ensures existing tenant schemas are reconciled to the current tenant
 * migration version before Hibernate builds its EntityManagerFactory.
 *
 * <p>This ordering is important because tenant RAG entities are validated by
 * Hibernate during EntityManagerFactory creation. An ApplicationRunner is too
 * late to repair an older tenant schema.</p>
 */
@Configuration
public class TenantSchemaMigrationStartupConfig {

    @Bean(name = "tenantSchemaMigrationStartupInitializer")
    @DependsOn("flyway")
    public TenantSchemaMigrationStartupInitializer
    tenantSchemaMigrationStartupInitializer(
            JdbcTemplate jdbcTemplate,
            TenantSchemaProvisioningService provisioningService) {
        return new TenantSchemaMigrationStartupInitializer(
                jdbcTemplate,
                provisioningService);
    }

    /**
     * Make Hibernate depend on the tenant migration initializer.
     *
     * <p>Spring Boot's public-schema Flyway migration remains the dependency
     * of the initializer itself, so the tenants table is available before
     * tenant schemas are enumerated.</p>
     */
    @Bean
    public static BeanFactoryPostProcessor
    tenantSchemaMigrationBeforeJpa() {
        return beanFactory -> {
            var beanDefinition =
                    beanFactory.getBeanDefinition("entityManagerFactory");

            String[] existing = beanDefinition.getDependsOn();
            String[] dependsOn = new String[
                    (existing == null ? 0 : existing.length) + 1];

            if (existing != null) {
                System.arraycopy(
                        existing,
                        0,
                        dependsOn,
                        0,
                        existing.length);
            }

            dependsOn[dependsOn.length - 1] =
                    "tenantSchemaMigrationStartupInitializer";

            beanDefinition.setDependsOn(dependsOn);
        };
    }

    public static final class TenantSchemaMigrationStartupInitializer {

        private final JdbcTemplate jdbcTemplate;
        private final TenantSchemaProvisioningService provisioningService;

        private TenantSchemaMigrationStartupInitializer(
                JdbcTemplate jdbcTemplate,
                TenantSchemaProvisioningService provisioningService) {
            this.jdbcTemplate = jdbcTemplate;
            this.provisioningService = provisioningService;
        }

        public void initialize() {
            List<String> schemas = jdbcTemplate.query(
                    """
                    SELECT schema_name
                    FROM tenants
                    WHERE schema_name IS NOT NULL
                      AND schema_name <> ''
                      AND status <> 'FAILED'
                    ORDER BY schema_name
                    """,
                    (rs, rowNum) -> rs.getString("schema_name"));

            for (String schemaName : schemas) {
                if (!schemaExists(schemaName)) {
                    // A failed/incomplete tenant provisioning attempt can leave
                    // a tenant metadata row behind before the physical schema
                    // is created. Never invoke Flyway against a schema that
                    // does not exist: with createSchemas=false Flyway fails
                    // while creating flyway_schema_history and prevents the
                    // entire ApplicationContext from starting.
                    org.slf4j.LoggerFactory.getLogger(
                            TenantSchemaMigrationStartupInitializer.class)
                            .warn(
                                    "Skipping tenant schema migration at startup: schema={} does not exist.",
                                    schemaName);
                    continue;
                }

                provisioningService.migrateSchema(schemaName);
            }
        }

        private boolean schemaExists(String schemaName) {
            Boolean exists = jdbcTemplate.queryForObject(
                    """
                    SELECT EXISTS (
                        SELECT 1
                        FROM pg_namespace
                        WHERE nspname = ?
                    )
                    """,
                    Boolean.class,
                    schemaName);

            return Boolean.TRUE.equals(exists);
        }

        /**
         * Invoked by Spring when the dependency bean is initialized.
         */
        @jakarta.annotation.PostConstruct
        void migrateExistingTenantSchemas() {
            initialize();
        }
    }
}
