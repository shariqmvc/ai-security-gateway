package com.ai.gateway.service;

import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.dto.ChatResponse;

import java.util.function.Consumer;

public interface GatewayService {

    ChatResponse process(ChatRequest request);

    void stream(ChatRequest request, Consumer<GatewayStreamEvent> eventConsumer);
}
