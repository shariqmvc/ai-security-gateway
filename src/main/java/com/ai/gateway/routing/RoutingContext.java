package com.ai.gateway.routing;

import com.ai.gateway.authentication.AuthenticationContext;
import com.ai.gateway.dto.ChatRequest;

public record RoutingContext(

        ChatRequest request,

        AuthenticationContext authenticationContext

) {
}