package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.entitlement.enums.Feature;
import com.ai.gateway.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PersonalFeatureEntitlementService {
    private final PersonalFeatureEntitlementProperties properties;

    public void validate(AuthenticationContext context, Feature feature) {
        if (context == null || !context.isPersonalPrincipal()
                || context.getPersonalAccountId() == null) {
            throw new BusinessException("Personal authentication is required.");
        }
        if (feature == null || !properties.getEnabled().contains(feature)) {
            throw new BusinessException(feature + " is disabled for this Personal account.");
        }
    }
}
