package com.ai.gateway.service;

import com.ai.gateway.entitlement.cache.EntitlementCache;
import com.ai.gateway.entitlement.dto.TenantEntitlementDto;
import com.ai.gateway.entitlement.dto.TenantEntitlementResponse;
import com.ai.gateway.entitlement.dto.UpdateTenantEntitlementRequest;
import com.ai.gateway.entitlement.entity.TenantEntitlement;
import com.ai.gateway.entitlement.enums.Feature;
import com.ai.gateway.entitlement.mapper.TenantEntitlementMapper;
import com.ai.gateway.entitlement.repository.TenantEntitlementRepository;
import com.ai.gateway.entitlement.service.impl.EntitlementServiceImpl;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.metrics.GatewayMetricsService;
import com.ai.gateway.provisioning.EntitlementProvisioningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EntitlementCacheConsistencyTest {

    @Mock
    private TenantEntitlementRepository repository;

    @Mock
    private TenantEntitlementMapper mapper;

    @Mock
    private EntitlementCache cache;

    @Mock
    private EntitlementProvisioningService provisioningService;

    @Mock
    private GatewayMetricsService metricsService;

    private EntitlementServiceImpl entitlementService;

    private UUID tenantId;

    private TenantEntitlement entity;

    private TenantEntitlementDto dto;

    @BeforeEach
    void setUp() {

        entitlementService =
                new EntitlementServiceImpl(
                        repository,
                        mapper,
                        cache,
                        provisioningService,
                        metricsService);

        tenantId = UUID.randomUUID();

        entity =
                TenantEntitlement.builder()
                        .tenantId(tenantId)
                        .features(Set.of(Feature.CHAT))
                        .requestsPerMinute(100L)
                        .requestsPerDay(1000L)
                        .monthlyTokenQuota(100000L)
                        .enabled(true)
                        .build();

        dto =
                TenantEntitlementDto.builder()
                        .tenantId(tenantId)
                        .features(Set.of(Feature.CHAT))
                        .requestsPerMinute(100L)
                        .requestsPerDay(1000L)
                        .monthlyTokenQuota(100000L)
                        .enabled(true)
                        .build();
    }

    @Test
    void shouldPopulateCacheOnCacheMiss() {

        when(cache.get(tenantId))
                .thenReturn(null);

        when(repository.findByTenantId(tenantId))
                .thenReturn(Optional.of(entity));

        when(mapper.toDto(entity))
                .thenReturn(dto);

        TenantEntitlementDto result =
                entitlementService.getDto(tenantId);

        assertEquals(
                tenantId,
                result.getTenantId());

        verify(repository)
                .findByTenantId(tenantId);

        verify(cache)
                .put(tenantId, dto);
    }

    @Test
    void shouldReturnCachedEntitlementWithoutDatabaseLookup() {

        when(cache.get(tenantId))
                .thenReturn(dto);

        TenantEntitlementDto result =
                entitlementService.getDto(tenantId);

        assertEquals(
                tenantId,
                result.getTenantId());

        verify(cache)
                .get(tenantId);

        verify(repository, never())
                .findByTenantId(any(UUID.class));
    }

    @Test
    void shouldRefreshCacheWhenEntitlementIsUpdated() {

        TenantEntitlementDto updatedDto =
                TenantEntitlementDto.builder()
                        .tenantId(tenantId)
                        .features(
                                Set.of(
                                        Feature.CHAT,
                                        Feature.EMBEDDING))
                        .requestsPerMinute(200L)
                        .requestsPerDay(2000L)
                        .monthlyTokenQuota(200000L)
                        .enabled(true)
                        .build();

        TenantEntitlementResponse response =
                mock(TenantEntitlementResponse.class);

        UpdateTenantEntitlementRequest request =
                UpdateTenantEntitlementRequest.builder()
                        .features(
                                Set.of(
                                        Feature.CHAT,
                                        Feature.EMBEDDING))
                        .requestsPerMinute(200L)
                        .requestsPerDay(2000L)
                        .monthlyTokenQuota(200000L)
                        .enabled(true)
                        .build();

        when(repository.findByTenantId(tenantId))
                .thenReturn(Optional.of(entity));

        when(repository.save(entity))
                .thenReturn(entity);

        when(mapper.toDto(entity))
                .thenReturn(updatedDto);

        when(mapper.toResponse(updatedDto))
                .thenReturn(response);

        TenantEntitlementResponse result =
                entitlementService.update(
                        tenantId,
                        request);

        assertEquals(
                response,
                result);

        verify(repository)
                .save(entity);

        verify(cache)
                .put(
                        tenantId,
                        updatedDto);
    }

    @Test
    void shouldEvictCacheWhenEntitlementIsDisabled() {

        when(repository.findByTenantId(tenantId))
                .thenReturn(Optional.of(entity));

        entitlementService.disable(tenantId);

        verify(repository)
                .save(entity);

        verify(cache)
                .evict(tenantId);
    }

    @Test
    void shouldEvictSpecificTenantCache() {

        entitlementService.evict(tenantId);

        verify(cache)
                .evict(tenantId);
    }

    @Test
    void shouldClearEntireEntitlementCache() {

        entitlementService.clearCache();

        verify(cache)
                .clear();
    }

    @Test
    void shouldRejectDisabledEntitlementOnCacheMiss() {

        TenantEntitlement disabledEntity =
                TenantEntitlement.builder()
                        .tenantId(tenantId)
                        .features(Set.of(Feature.CHAT))
                        .enabled(false)
                        .build();

        when(cache.get(tenantId))
                .thenReturn(null);

        when(repository.findByTenantId(tenantId))
                .thenReturn(
                        Optional.of(disabledEntity));

        assertThrows(
                BusinessException.class,
                () ->
                        entitlementService.getDto(
                                tenantId));

        verify(cache, never())
                .put(
                        any(UUID.class),
                        any(TenantEntitlementDto.class));
    }

    @Test
    void shouldRejectDisabledEntitlementAfterCacheEviction() {

        // 1. Initially the entitlement is enabled and cached.
        entity.setEnabled(true);

        when(cache.get(tenantId))
                .thenReturn(dto);

        TenantEntitlementDto cached =
                entitlementService.getDto(tenantId);

        assertEquals(
                tenantId,
                cached.getTenantId());

        // 2. Database entitlement is now disabled.
        entity.setEnabled(false);

        when(repository.findByTenantId(tenantId))
                .thenReturn(Optional.of(entity));

        // 3. Disable operation must evict the stale cache.
        entitlementService.disable(tenantId);

        verify(cache)
                .evict(tenantId);

        // 4. Simulate the cache being empty after eviction.
        when(cache.get(tenantId))
                .thenReturn(null);

        // 5. Next lookup must hit DB and reject disabled entitlement.
        assertThrows(
                BusinessException.class,
                () ->
                        entitlementService.getDto(tenantId));

        verify(repository, atLeastOnce())
                .findByTenantId(tenantId);

        // Disabled entitlement must never be inserted into cache.
        verify(cache, never())
                .put(
                        eq(tenantId),
                        any(TenantEntitlementDto.class));
    }
}