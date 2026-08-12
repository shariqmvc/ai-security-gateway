package com.ai.gateway.routing;

import com.ai.gateway.enums.Provider;

public record RoutingDecision(

        Provider provider,

        String model,

        RoutingStrategy strategy

) {
}
