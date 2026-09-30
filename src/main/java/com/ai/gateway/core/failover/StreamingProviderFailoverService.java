package com.ai.gateway.core.failover;

import com.ai.gateway.core.contract.AIRequest;
import com.ai.gateway.core.provider.AIStreamResult;

import java.util.function.Consumer;

/**
 * Streaming provider execution boundary with bounded provider failover.
 *
 * <p>Failover is only safe before any response content has been emitted to the
 * caller. Once a delta is delivered, the current provider owns the response
 * stream and the failure is propagated rather than switching providers and
 * corrupting the conversation.</p>
 */
public interface StreamingProviderFailoverService {

    AIStreamResult stream(
            AIRequest request,
            Consumer<String> deltaConsumer);
}
