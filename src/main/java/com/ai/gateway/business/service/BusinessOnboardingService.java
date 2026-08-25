package com.ai.gateway.business.service;

import com.ai.gateway.business.dto.BusinessOnboardingRequest;
import com.ai.gateway.business.dto.BusinessOnboardingResponse;

import java.util.UUID;

public interface BusinessOnboardingService {
    BusinessOnboardingResponse onboard(BusinessOnboardingRequest request);
    BusinessOnboardingResponse get(UUID businessId);
    BusinessOnboardingResponse retry(UUID businessId);
}
