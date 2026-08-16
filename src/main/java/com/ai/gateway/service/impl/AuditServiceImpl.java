package com.ai.gateway.service.impl;

import com.ai.gateway.entity.RequestAudit;
import com.ai.gateway.enums.AuditStatus;
import com.ai.gateway.repository.RequestAuditRepository;
import com.ai.gateway.service.AuditService;
import com.ai.gateway.tenant.TenantSchemaRoutingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final RequestAuditRepository repository;
    private final TenantSchemaRoutingService tenantSchemaRoutingService;

    @Transactional
    public void save(
            UUID requestId,
            String maskedPrompt,
            String maskedResponse,
            long latency,
            String model,
            String provider,
            AuditStatus status) {

        tenantSchemaRoutingService.useTenantSchema();

        RequestAudit audit = RequestAudit.builder()
                .requestUuid(requestId)
                .maskedPrompt(maskedPrompt)
                .maskedResponse(maskedResponse)
                .latencyMs(latency)
                .modelName(model)
                .provider(provider)
                .status(status)
                .createdAt(LocalDateTime.now())
                .build();

        repository.save(audit);
    }
}