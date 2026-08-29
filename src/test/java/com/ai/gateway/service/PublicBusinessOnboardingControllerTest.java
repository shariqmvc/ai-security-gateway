package com.ai.gateway.service;

import com.ai.gateway.business.PublicBusinessOnboardingController;
import com.ai.gateway.business.dto.BusinessOnboardingRequest;
import com.ai.gateway.business.dto.BusinessOnboardingResponse;
import com.ai.gateway.business.service.BusinessOnboardingService;
import com.ai.gateway.business.BusinessType;
import com.ai.gateway.entitlement.enums.Plan;
import com.ai.gateway.core.model.Provider;
import com.ai.gateway.tenant.TenantType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicBusinessOnboardingControllerTest {

    @Test
    void publicOnboardingDelegatesWithoutPlatformAuthorization() {
        BusinessOnboardingService service = mock(BusinessOnboardingService.class);
        PublicBusinessOnboardingController controller =
                new PublicBusinessOnboardingController(service);

        BusinessOnboardingRequest request = BusinessOnboardingRequest.builder()
                .tenantCode("PUBLIC-POSTMAN-001")
                .tenantName("Public Postman Test")
                .plan(Plan.FREE)
                .tenantType(TenantType.STANDARD)
                .defaultProvider(Provider.OLLAMA)
                .defaultModel("llama3.2:3b")
                .businessName("Public Postman Business")
                .businessType(BusinessType.STANDARD)
                .source("POSTMAN_PUBLIC_TEST")
                .build();

        BusinessOnboardingResponse expected = BusinessOnboardingResponse.builder().build();
        when(service.onboard(request)).thenReturn(expected);

        assertSame(expected, controller.onboard(request));
        verify(service).onboard(request);
        verifyNoMoreInteractions(service);
    }
}
