package com.ai.gateway.business;

import com.ai.gateway.business.dto.BusinessOnboardingRequest;
import com.ai.gateway.business.dto.BusinessOnboardingResponse;
import com.ai.gateway.business.service.BusinessOnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/public/businesses")
@RequiredArgsConstructor
public class PublicBusinessOnboardingController {

    private final BusinessOnboardingService onboardingService;

    /**
     * Public self-service business signup.
     *
     * No pre-existing tenant/API key is required because this operation
     * establishes the initial Business/Tenant identity.
     */
    @PostMapping("/onboard")
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessOnboardingResponse onboard(
            @Valid @RequestBody BusinessOnboardingRequest request) {
        return onboardingService.onboard(request);
    }
}
