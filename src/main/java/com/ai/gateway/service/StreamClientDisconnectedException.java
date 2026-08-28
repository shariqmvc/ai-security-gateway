package com.ai.gateway.service;

/**
 * Signals that the downstream HTTP client disconnected while the gateway was
 * writing a streaming response. This is an expected terminal condition and
 * must not be reported as a provider failure or cause a second SSE error write.
 */
public class StreamClientDisconnectedException extends RuntimeException {

    public StreamClientDisconnectedException(Throwable cause) {
        super("Streaming client disconnected.", cause);
    }
}
