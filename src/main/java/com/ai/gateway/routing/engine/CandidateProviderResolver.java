package com.ai.gateway.routing.engine;

import com.ai.gateway.enums.Provider;
import com.ai.gateway.routing.policy.RoutingPolicy;

import java.util.List;

public interface CandidateProviderResolver {

    List<Provider> resolve(RoutingPolicy policy);
}