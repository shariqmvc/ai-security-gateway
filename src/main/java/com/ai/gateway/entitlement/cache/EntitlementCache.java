package com.ai.gateway.entitlement.cache;

import com.ai.gateway.entitlement.dto.TenantEntitlementDto;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class EntitlementCache {

    private final ConcurrentMap<UUID, TenantEntitlementDto> cache =
            new ConcurrentHashMap<>();

    public TenantEntitlementDto get(UUID tenantId) {
        return cache.get(tenantId);
    }

    public void put(
            UUID tenantId,
            TenantEntitlementDto entitlement) {

        cache.put(
                tenantId,
                entitlement);

    }

    public void evict(UUID tenantId) {

        cache.remove(tenantId);

    }

    public void clear() {

        cache.clear();

    }

}
