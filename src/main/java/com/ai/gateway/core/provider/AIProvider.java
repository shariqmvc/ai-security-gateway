package com.ai.gateway.core.provider;

import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.contract.AIResponse;
import com.ai.gateway.core.model.Provider;

public interface AIProvider {
    Provider provider();

    String defaultModel();

    AIResponse chat(AIRequest request);

}
