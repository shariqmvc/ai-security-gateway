package com.ai.gateway.provisioning;

import com.ai.gateway.security.ApiKeyProvisioningResult;
import com.ai.gateway.security.ApiKeyService;
import com.ai.gateway.security.AuthorizationService;
import com.ai.gateway.security.SecurityRole;
import com.ai.gateway.tenant.Tenant;
import com.ai.gateway.tenant.TenantRepository;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/tenants/{tenantId}")
@RequiredArgsConstructor
public class TenantProvisioningController {

    private final TenantProvisioningService provisioningService;
    private final TenantRepository tenantRepository;
    private final ApiKeyService apiKeyService;
    private final AuthorizationService authorizationService;

    @GetMapping("/provisioning")
    public TenantProvisioningStatus status(@PathVariable UUID tenantId) {
        authorizationService.requirePlatformRole(
                SecurityRole.PLATFORM_OWNER,
                SecurityRole.PLATFORM_ADMIN,
                SecurityRole.PLATFORM_OPERATIONS);
        return provisioningService.status(tenantId);
    }

    @PostMapping("/provisioning/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public TenantProvisioningStatus retry(@PathVariable UUID tenantId) {
        authorizationService.requirePlatformRole(
                SecurityRole.PLATFORM_OWNER,
                SecurityRole.PLATFORM_ADMIN);
        provisioningService.retry(tenantId);
        return provisioningService.status(tenantId);
    }

    @PostMapping("/api-keys")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiKeyProvisioningResult rotateApiKey(
            @PathVariable UUID tenantId,
            @RequestParam(required = false)
            @Size(max = 255) String clientName,
            @RequestParam(defaultValue = "TENANT_USER")
            SecurityRole role) {

        authorizationService.requirePlatformRole(
                SecurityRole.PLATFORM_OWNER,
                SecurityRole.PLATFORM_ADMIN);

        if (role == null || !role.isTenantRole()) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only tenant roles may be assigned to tenant API keys.");
        }

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Tenant not found: " + tenantId));

        return apiKeyService.rotate(tenant, clientName, role);
    }
    @DeleteMapping("/api-keys/{apiKeyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeApiKey(
            @PathVariable UUID tenantId,
            @PathVariable UUID apiKeyId) {

        authorizationService.requirePlatformRole(
                SecurityRole.PLATFORM_OWNER,
                SecurityRole.PLATFORM_ADMIN);

        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "Tenant not found: " + tenantId));

        apiKeyService.revoke(tenant, apiKeyId);
    }

}
