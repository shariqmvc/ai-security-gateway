package com.ai.gateway.provisioning;

import com.ai.gateway.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantSchemaExecutionService {

    private final EntityManager entityManager;

    @Transactional
    public void setTenantSchema() {

        String schema =
                "tenant_" +
                        TenantContext.require()
                                .toString()
                                .replace("-", "")
                                .toLowerCase();

        entityManager
                .createNativeQuery(
                        "SET LOCAL search_path TO "
                                + quoteIdentifier(schema))
                .executeUpdate();
    }

    private String quoteIdentifier(String identifier) {

        if (!identifier.matches(
                "[a-zA-Z_][a-zA-Z0-9_]*")) {

            throw new IllegalArgumentException(
                    "Invalid schema identifier.");
        }

        return "\"" + identifier + "\"";
    }
}