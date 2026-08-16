package com.ai.gateway.provisioning;

import com.ai.gateway.tenant.Tenant;
import org.springframework.stereotype.Component;

@Component
public class TenantSchemaNameResolver {

    public String resolve(Tenant tenant) {
        if (tenant.getId() == null) {
            throw new IllegalArgumentException(
                    "Tenant ID is required to resolve schema name.");
        }

        return "tenant_" +
                tenant.getId()
                        .toString()
                        .replace("-", "")
                        .toLowerCase();
    }
}