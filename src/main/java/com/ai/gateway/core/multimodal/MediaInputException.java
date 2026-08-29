package com.ai.gateway.core.multimodal;

/**
 * Indicates that caller-supplied media could not be acquired, validated,
 * decoded, or translated into a provider-ready representation.
 *
 * This is a request/media failure, not a provider availability failure.
 * It must not trigger provider failover or provider circuit breaking.
 */
public class MediaInputException extends RuntimeException {

    public MediaInputException(String message) {
        super(message);
    }

    public MediaInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
