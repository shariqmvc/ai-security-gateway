package com.ai.gateway.provider;

import com.ai.gateway.dto.AIRequest;

import java.util.function.Consumer;

/**
 * Optional provider capability for incremental token streaming.
 * Providers that do not support streaming remain usable through AIProvider.
 */
public interface StreamingAIProvider {

    /**
     * Streams normalized text deltas to the consumer and returns final usage/model metadata.
     */
    AIStreamResult stream(AIRequest request, Consumer<String> deltaConsumer);
}
