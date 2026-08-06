package com.ai.gateway.provider;

import com.ai.gateway.dto.AIRequest;
import com.ai.gateway.dto.AIResponse;
import com.ai.gateway.enums.Provider;

public interface AIProvider {
    Provider provider();

    String defaultModel();

    AIResponse chat(AIRequest request);

}
