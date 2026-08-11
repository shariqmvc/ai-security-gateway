package com.ai.gateway.entitlement.mapper;

import com.ai.gateway.entitlement.enums.Feature;
import com.ai.gateway.enums.Provider;

public final class ProviderFeatureMapper {

    private ProviderFeatureMapper() {
    }

    public static Feature toFeature(
            Provider provider) {

        return switch (provider) {

            case OPENAI -> Feature.OPENAI;
            case GEMINI -> Feature.GEMINI;
            case CLAUDE -> Feature.CLAUDE;
            case OLLAMA -> Feature.OLLAMA;

        };

    }

}