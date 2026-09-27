package com.ai.gateway.core.routing.registry;

public final class ModelCapabilities {

    private ModelCapabilities() {
    }

    public static final String CHAT = "CHAT";

    public static final String STREAMING = "STREAMING";

    public static final String VISION = "VISION";

    public static final String AUDIO = "AUDIO";

    /** Provider-native transient document/file input. */
    public static final String DOCUMENT_INPUT = "DOCUMENT_INPUT";

    public static final String TOOLS = "TOOLS";

    public static final String REASONING = "REASONING";
}
