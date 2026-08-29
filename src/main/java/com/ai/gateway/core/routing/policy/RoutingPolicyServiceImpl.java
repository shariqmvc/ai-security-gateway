package com.ai.gateway.core.routing.policy;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RoutingPolicyServiceImpl
        implements RoutingPolicyService {

    @Override
    public RoutingPolicy resolve(
            ChatRequest request,
            AuthenticationContext authenticationContext) {

        if (authenticationContext == null) {
            throw new IllegalArgumentException(
                    "Authentication context is required.");
        }

        /*
         * 6.4.7 foundation:
         *
         * The current implementation has tenant defaults but does not yet
         * have persisted routing-policy configuration.
         *
         * Therefore we expose the tenant defaults as the initial preferred
         * routing policy while keeping the policy boundary independent from
         * RoutingService.
         *
         * Later phases can replace this resolution logic with persisted
         * tenant/organization/provider policy configuration.
         */

        return new RoutingPolicy(
                true,
                java.util.List.of(),
                java.util.List.of(),
                authenticationContext.getDefaultProvider(),
                authenticationContext.getDefaultModel());
    }
}