package com.ai.gateway.core.routing.policy;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.core.contract.ChatRequest;

public interface RoutingPolicyService {

    RoutingPolicy resolve(
            ChatRequest request,
            AuthenticationContext authenticationContext);
}