package com.ai.gateway.business;

import com.ai.gateway.business.dto.BusinessOnboardingRequest;
import com.ai.gateway.business.dto.BusinessOnboardingResponse;
import com.ai.gateway.business.service.BusinessOnboardingService;
import com.ai.gateway.security.AuthorizationService;
import com.ai.gateway.security.SecurityRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/admin/businesses")
@RequiredArgsConstructor
public class BusinessOnboardingController {

    private final BusinessOnboardingService onboardingService;
    private final AuthorizationService authorizationService;

    @PostMapping("/onboard")
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessOnboardingResponse onboard(
            @Valid @RequestBody BusinessOnboardingRequest request) {
        authorizationService.requirePlatformRole(
                SecurityRole.PLATFORM_OWNER,
                SecurityRole.PLATFORM_ADMIN);
        return onboardingService.onboard(request);
    }

    @GetMapping("/{businessId}")
    public BusinessOnboardingResponse get(@PathVariable UUID businessId) {
        authorizationService.requirePlatformRole(
                SecurityRole.PLATFORM_OWNER,
                SecurityRole.PLATFORM_ADMIN,
                SecurityRole.PLATFORM_OPERATIONS,
                SecurityRole.PLATFORM_SUPPORT);
        return onboardingService.get(businessId);
    }

    @PostMapping("/{businessId}/retry")
    public BusinessOnboardingResponse retry(@PathVariable UUID businessId) {
        authorizationService.requirePlatformRole(
                SecurityRole.PLATFORM_OWNER,
                SecurityRole.PLATFORM_ADMIN,
                SecurityRole.PLATFORM_OPERATIONS);
        return onboardingService.retry(businessId);
    }
}
