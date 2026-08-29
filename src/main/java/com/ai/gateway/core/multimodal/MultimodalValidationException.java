package com.ai.gateway.core.multimodal;

public class MultimodalValidationException extends MediaInputException {

    public MultimodalValidationException(String message) {
        super(message);
    }

    public MultimodalValidationException(
            String message,
            Throwable cause) {
        super(message, cause);
    }
}