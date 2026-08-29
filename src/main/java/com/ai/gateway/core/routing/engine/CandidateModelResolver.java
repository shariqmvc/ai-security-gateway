package com.ai.gateway.core.routing.engine;

import com.ai.gateway.core.model.Provider;
import com.ai.gateway.core.routing.policy.RoutingPolicy;

import java.util.List;

public interface CandidateModelResolver {

    List<String> resolve(
            Provider provider,
            RoutingPolicy policy);
}