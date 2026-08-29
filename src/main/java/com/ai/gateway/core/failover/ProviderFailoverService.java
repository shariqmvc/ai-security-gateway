package com.ai.gateway.core.failover;

import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.contract.AIResponse;

/**
 * Executes an AI request against the selected provider and, when configured,
 * fails over to an ordered list of alternative providers.
 */
public interface ProviderFailoverService {

    AIResponse execute(AIRequest request);
}
