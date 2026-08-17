package com.ai.gateway.tenant;

import com.ai.gateway.tenant.dto.TenantRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.ai.gateway.security.AuthorizationService;
import com.ai.gateway.security.SecurityRole;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;
    private final AuthorizationService authorizationService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Tenant create(
            @Valid
            @RequestBody
            TenantRequest request) {

        authorizationService.requirePlatformRole(
                SecurityRole.PLATFORM_OWNER,
                SecurityRole.PLATFORM_ADMIN);
        return tenantService.create(request);
    }
}
