package com.ai.gateway.entitlement.controller;

import com.ai.gateway.entitlement.dto.CreateTenantEntitlementRequest;
import com.ai.gateway.entitlement.dto.TenantEntitlementDto;
import com.ai.gateway.entitlement.dto.TenantEntitlementResponse;
import com.ai.gateway.entitlement.dto.UpdateTenantEntitlementRequest;
import com.ai.gateway.entitlement.service.EntitlementService;
import com.ai.gateway.quota.dto.TenantQuotaUsageResponse;
import com.ai.gateway.quota.service.QuotaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/entitlements")
@RequiredArgsConstructor
public class EntitlementController {

    private final EntitlementService service;

    private final QuotaService quotaService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantEntitlementResponse create(
            @Valid
            @RequestBody
            CreateTenantEntitlementRequest request) {

        return service.create(request);

    }

    @GetMapping("/{tenantId}")
    public TenantEntitlementResponse get(
            @PathVariable UUID tenantId) {

        return service.get(tenantId);

    }

    @PutMapping("/{tenantId}")
    public TenantEntitlementResponse update(

            @PathVariable UUID tenantId,

          //  @Valid
            @RequestBody
            UpdateTenantEntitlementRequest request) {

        return service.update(
                tenantId,
                request);

    }

    @DeleteMapping("/{tenantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(
            @PathVariable UUID tenantId) {

        service.disable(tenantId);

    }

    @PostMapping("/tenants/{tenantId}/provision")
    public ResponseEntity<TenantEntitlementResponse> provision(
            @PathVariable UUID tenantId) {

        return ResponseEntity.ok(
                service.provision(tenantId));
    }

    @GetMapping("/{tenantId}/quota")
    public TenantQuotaUsageResponse getQuotaUsage(
            @PathVariable UUID tenantId) {

        return quotaService.getUsage(
                tenantId);
    }
}