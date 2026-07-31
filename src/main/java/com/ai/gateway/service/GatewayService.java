package com.ai.gateway.service;

import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.dto.ChatResponse;

public interface GatewayService {

    ChatResponse process(ChatRequest request);
}
