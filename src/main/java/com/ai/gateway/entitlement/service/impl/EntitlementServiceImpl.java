package com.ai.gateway.entitlement.service.impl;

import com.ai.gateway.entitlement.cache.EntitlementCache;
import com.ai.gateway.entitlement.dto.CreateTenantEntitlementRequest;
import com.ai.gateway.entitlement.dto.TenantEntitlementDto;
import com.ai.gateway.entitlement.dto.TenantEntitlementResponse;
import com.ai.gateway.entitlement.dto.UpdateTenantEntitlementRequest;
import com.ai.gateway.entitlement.entity.TenantEntitlement;
import com.ai.gateway.entitlement.enums.Feature;
import com.ai.gateway.entitlement.mapper.TenantEntitlementMapper;
import com.ai.gateway.entitlement.repository.TenantEntitlementRepository;
import com.ai.gateway.entitlement.service.EntitlementService;
import com.ai.gateway.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class EntitlementServiceImpl
        implements EntitlementService {

    private final TenantEntitlementRepository repository;

    private final TenantEntitlementMapper mapper;

    private final EntitlementCache cache;

    @Override
    public TenantEntitlementResponse create(
            CreateTenantEntitlementRequest request) {

        if (repository.findByTenantId(request.getTenantId()).isPresent()) {

            throw new BusinessException(
                    "Tenant entitlement already exists.");
        }

        TenantEntitlement entity =
                mapper.toEntity(request);

        entity.setEnabled(true);

        entity = repository.save(entity);

        TenantEntitlementDto dto =
                mapper.toDto(entity);

        cache.put(
                entity.getTenantId(),
                dto);

        return mapper.toResponse(dto);
    }

    @Override
    @Transactional(readOnly = true)
    public TenantEntitlementResponse get(
            UUID tenantId) {

        return mapper.toResponse(
                getDto(tenantId));
    }

    @Override
    @Transactional(readOnly = true)
    public TenantEntitlementDto getDto(
            UUID tenantId) {

        TenantEntitlementDto cached =
                cache.get(tenantId);

        if (cached != null) {

            log.debug(
                    "Entitlement cache hit. tenant={}",
                    tenantId);

            return cached;
        }

        log.debug(
                "Entitlement cache miss. tenant={}",
                tenantId);

        TenantEntitlement entity =
                repository.findByTenantId(tenantId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "Tenant entitlement not found."));

        if (!entity.isEnabled()) {

            throw new BusinessException(
                    "Tenant entitlement is disabled.");
        }

        TenantEntitlementDto dto =
                mapper.toDto(entity);

        cache.put(
                tenantId,
                dto);

        return dto;
    }

    @Override
    public TenantEntitlementResponse update(
            UUID tenantId,
            UpdateTenantEntitlementRequest request) {

        TenantEntitlement entity =
                repository.findByTenantId(tenantId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "Tenant entitlement not found."));

        mapper.update(
                entity,
                request);

        entity = repository.save(entity);

        TenantEntitlementDto dto =
                mapper.toDto(entity);

        cache.put(
                tenantId,
                dto);

        return mapper.toResponse(dto);
    }

    @Override
    public void disable(
            UUID tenantId) {

        TenantEntitlement entity =
                repository.findByTenantId(tenantId)
                        .orElseThrow(() ->
                                new BusinessException(
                                        "Tenant entitlement not found."));

        entity.setEnabled(false);

        repository.save(entity);

        cache.evict(tenantId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasFeature(
            UUID tenantId,
            Feature feature) {

        TenantEntitlementDto entitlement =
                getDto(tenantId);

        return entitlement.getFeatures() != null
                && entitlement.getFeatures().contains(feature);
    }

    @Override
    public void evict(
            UUID tenantId) {

        cache.evict(tenantId);

        log.info(
                "Entitlement cache evicted. tenant={}",
                tenantId);
    }

    @Override
    public void clearCache() {

        cache.clear();

        log.info(
                "Entitlement cache cleared.");
    }
}
