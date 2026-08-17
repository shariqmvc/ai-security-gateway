package com.ai.gateway.service.impl;

import com.ai.gateway.dto.DetectedPII;
import com.ai.gateway.entity.TokenVault;
import com.ai.gateway.repository.TokenVaultRepository;
import com.ai.gateway.service.TokenVaultService;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import com.ai.gateway.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Persists and restores PII tokens strictly inside the authenticated tenant
 * operational schema. Token vault data is tenant-sensitive and must never
 * fall back to the public/control-plane schema.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenVaultServiceImpl implements TokenVaultService {

    private final TokenVaultRepository repository;
    private final EncryptionUtil encryptionUtil;
    private final TenantSchemaRoutingService tenantSchemaRoutingService;

    @Override
    @Transactional
    public void save(UUID requestId, List<DetectedPII> detectedValues) {

        if (requestId == null || detectedValues == null || detectedValues.isEmpty()) {
            return;
        }

        // Fail closed: token-vault writes must always use the authenticated
        // tenant schema established by AuthenticationFilter.
        tenantSchemaRoutingService.useTenantSchema();

        List<TokenVault> entities = new ArrayList<>();

        for (DetectedPII pii : detectedValues) {
            if (pii == null) {
                continue;
            }

            entities.add(
                    TokenVault.builder()
                            .requestUuid(requestId)
                            .token(pii.getToken())
                            .encryptedValue(
                                    encryptionUtil.encrypt(pii.getOriginalValue()))
                            .piiType(pii.getPiiType())
                            .build());
        }

        if (!entities.isEmpty()) {
            repository.saveAll(entities);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<TokenVault> getTokens(UUID requestId) {

        if (requestId == null) {
            return List.of();
        }

        // The request UUID alone is not a tenant boundary. The physical
        // tenant schema is the security boundary for this lookup.
        tenantSchemaRoutingService.useTenantSchema();

        return repository.findByRequestUuid(requestId);
    }
}
