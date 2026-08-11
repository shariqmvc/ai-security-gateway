package com.ai.gateway.tenant;

import com.ai.gateway.tenant.dto.TenantRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Tenant create(
            @Valid
            @RequestBody
            TenantRequest request) {

        return tenantService.create(request);
    }
}
