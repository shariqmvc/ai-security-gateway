package com.ai.gateway.entitlement.controller;

import com.ai.gateway.budget.dto.BudgetUsageResponse;
import com.ai.gateway.budget.service.BudgetService;
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
import com.ai.gateway.security.AuthorizationService;
import com.ai.gateway.security.SecurityRole;

import java.util.UUID;

@RestController
@RequestMapping("/admin/entitlements")
@RequiredArgsConstructor
public class EntitlementController {

    private final EntitlementService service;

    private final QuotaService quotaService;

    private final BudgetService budgetService;
    private final AuthorizationService authorizationService;

    private void requirePlatformAdmin() {
        authorizationService.requirePlatformRole(
                SecurityRole.PLATFORM_OWNER,
                SecurityRole.PLATFORM_ADMIN);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantEntitlementResponse create(
            @Valid
            @RequestBody
            CreateTenantEntitlementRequest request) {

        requirePlatformAdmin();
        return service.create(request);

    }

    @GetMapping("/{tenantId}")
    public TenantEntitlementResponse get(
            @PathVariable UUID tenantId) {

        requirePlatformAdmin();
        return service.get(tenantId);

    }

    @PutMapping("/{tenantId}")
    public TenantEntitlementResponse update(

            @PathVariable UUID tenantId,

          //  @Valid
            @RequestBody
            UpdateTenantEntitlementRequest request) {

        requirePlatformAdmin();
        return service.update(
                tenantId,
                request);

    }

    @DeleteMapping("/{tenantId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(
            @PathVariable UUID tenantId) {

        requirePlatformAdmin();
        service.disable(tenantId);

    }

    @PostMapping("/tenants/{tenantId}/provision")
    public ResponseEntity<TenantEntitlementResponse> provision(
            @PathVariable UUID tenantId) {

        requirePlatformAdmin();
        return ResponseEntity.ok(
                service.provision(tenantId));
    }

    @GetMapping("/tenants/{tenantId}/quota")
    public TenantQuotaUsageResponse getQuotaUsage(
            @PathVariable UUID tenantId) {

        requirePlatformAdmin();
        return quotaService.getUsage(
                tenantId);
    }

    @GetMapping("/tenants/{tenantId}/budget")
    public BudgetUsageResponse getBudgetUsage(
            @PathVariable UUID tenantId) {

        requirePlatformAdmin();
        return budgetService.getUsage(
                tenantId);
    }
}