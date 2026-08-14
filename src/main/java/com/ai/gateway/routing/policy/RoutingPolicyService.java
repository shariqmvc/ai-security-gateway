package com.ai.gateway.routing.policy;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.ChatRequest;

public interface RoutingPolicyService {

    RoutingPolicy resolve(
            ChatRequest request,
            AuthenticationContext authenticationContext);
}