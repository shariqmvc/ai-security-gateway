package com.ai.gateway.personal;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.entitlement.enums.Feature;
import com.ai.gateway.exception.BusinessException;
import com.ai.gateway.personal.policy.service.PersonalAccountPolicyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PersonalFeatureEntitlementService {

    private final PersonalAccountPolicyService policyService;

    public void validate(AuthenticationContext context, Feature feature) {
        if (context == null || !context.isPersonalPrincipal()
                || context.getPersonalAccountId() == null) {
            throw new BusinessException("Personal authentication is required.");
        }

        if (feature == null
                || !policyService.enabledFeatures(context.getPersonalAccountId()).contains(feature)) {
            throw new BusinessException(feature + " is disabled for this Personal account.");
        }
    }
}
