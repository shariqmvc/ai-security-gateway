package com.ai.gateway.service;

import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.enums.Provider;
import com.ai.gateway.provisioning.TenantSchemaCreationService;
import com.ai.gateway.provisioning.TenantSchemaMigrationReconciliationService;
import com.ai.gateway.provisioning.TenantSchemaNameResolver;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantRepository;
import com.ai.gateway.tenant.TenantStatus;
import com.ai.gateway.tenant.TenantType;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class TenantSchemaMigrationReconciliationIntegrationTest {

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private TenantSchemaCreationService schemaCreationService;

    @Autowired
    private TenantSchemaMigrationReconciliationService reconciliationService;

    @Autowired
    private TenantSchemaNameResolver schemaNameResolver;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    private UUID tenantId;
    private String schemaName;

    @AfterEach
    void cleanup() {

        if (schemaName != null) {
            jdbcTemplate.execute(
                    "DROP SCHEMA IF EXISTS \""
                            + schemaName.replace("\"", "\"\"")
                            + "\" CASCADE");
        }

        if (tenantId != null) {
            tenantRepository.deleteById(tenantId);
        }
    }

    @Test
    void shouldUpgradeExistingV1TenantSchemaToCurrentVersion() {

        Tenant tenant = Tenant.builder()
                .tenantCode("RAG-RECON-" + UUID.randomUUID())
                .tenantName("RAG Reconciliation Tenant")
                .status(TenantStatus.ACTIVE)
                .type(TenantType.STANDARD)
                .plan(Plan.PROFESSIONAL)
                .defaultProvider(Provider.GEMINI)
                .defaultModel("gemini-3.6-flash")
                .build();

        tenant = tenantRepository.saveAndFlush(tenant);
        tenantId = tenant.getId();

        schemaName = schemaNameResolver.resolve(tenant);
        tenant.setSchemaName(schemaName);
        tenantRepository.saveAndFlush(tenant);

        schemaCreationService.createSchema(schemaName);

        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .createSchemas(false)
                .locations("classpath:db/tenant-migration")
                .target("1")
                .load()
                .migrate();

        assertEquals(
                1,
                flywayVersion(schemaName));

        assertFalseTableExists(schemaName, "knowledge_base");
        assertFalseTableExists(schemaName, "rag_document");
        assertFalseTableExists(schemaName, "rag_document_chunk");

        TenantSchemaMigrationReconciliationService.ReconciliationResult result =
                reconciliationService.reconcileAll();

        assertTrue(result.migratedTenants() >= 1);

        assertEquals(
                7,
                flywayVersion(schemaName));

        assertTenantTableExists(schemaName, "knowledge_base");
        assertTenantTableExists(schemaName, "rag_document");
        assertTenantTableExists(schemaName, "rag_document_chunk");
        assertTenantIndexExists(schemaName, "uk_rag_document_kb_checksum");
        assertTenantColumnExists(schemaName, "rag_document_chunk", "embedding");
        assertTenantColumnExists(schemaName, "rag_document_chunk", "embedding_provider");
        assertTenantColumnExists(schemaName, "rag_document_chunk", "embedding_model");
        assertTenantColumnExists(schemaName, "rag_document_chunk", "embedding_dimension");
        assertTenantColumnExists(schemaName, "rag_document_chunk", "embedded_at");
        assertTenantColumnExists(schemaName, "rag_document_chunk", "record_id");
        assertTenantColumnExists(schemaName, "rag_document_chunk", "section_id");
        assertTenantColumnExists(schemaName, "rag_document_chunk", "chunk_id");
        assertTenantIndexExists(schemaName, "idx_rag_chunk_record");
        assertTenantIndexExists(schemaName, "idx_rag_chunk_record_section");
    }

    private int flywayVersion(String schema) {

        Integer version = jdbcTemplate.queryForObject(
                """
                SELECT MAX(version::integer)
                FROM %s.flyway_schema_history
                """.formatted(quote(schema)),
                Integer.class);

        return version == null ? 0 : version;
    }

    private void assertTenantTableExists(
            String schema,
            String table) {

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_name = ?
                """,
                Integer.class,
                schema,
                table);

        assertEquals(
                1,
                count,
                "Expected table " + schema + "." + table);
    }

    private void assertTenantColumnExists(
            String schema,
            String table,
            String column) {

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = ?
                  AND table_name = ?
                  AND column_name = ?
                """,
                Integer.class,
                schema,
                table,
                column);

        assertEquals(
                1,
                count,
                "Expected column " + schema + "." + table + "." + column);
    }

    private void assertTenantIndexExists(String schema, String indexName) {

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_indexes
                WHERE schemaname = ?
                  AND indexname = ?
                """,
                Integer.class,
                schema,
                indexName);

        assertEquals(1, count,
                "Expected index " + schema + "." + indexName);
    }

    private void assertFalseTableExists(
            String schema,
            String table) {

        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_name = ?
                """,
                Integer.class,
                schema,
                table);

        assertEquals(
                0,
                count,
                "Expected table " + schema + "." + table + " to be absent");
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
