package com.ai.gateway.provisioning;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantSchemaCreationService {

    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createSchema(String schemaName) {

        String quoted =
                "\"" + schemaName.replace("\"", "\"\"") + "\"";

        jdbcTemplate.execute(
                "CREATE SCHEMA IF NOT EXISTS " + quoted);
    }
}