package com.ai.gateway.service;

import com.ai.gateway.core.contract.ChatRequest;
import com.ai.gateway.core.contract.ChatResponse;

import java.util.function.Consumer;

public interface GatewayService {

    ChatResponse process(ChatRequest request);

    /**
     * Performs all request admission checks that must complete before an SSE
     * response is committed. In particular, Personal Firewall checks happen
     * here so blocked/unavailable requests can still return HTTP 403/503.
     */
    StreamAdmission preflightStream(ChatRequest request);

    StreamAdmission preflightStream(ChatRequest request, String endpoint, String operation);

    void stream(ChatRequest request, Consumer<GatewayStreamEvent> eventConsumer);

    void stream(ChatRequest request, StreamAdmission admission, Consumer<GatewayStreamEvent> eventConsumer);
}
