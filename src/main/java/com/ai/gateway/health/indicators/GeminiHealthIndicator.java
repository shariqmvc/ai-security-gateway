package com.ai.gateway.health.indicators;

import com.ai.gateway.config.GeminiConfig;
import com.ai.gateway.enums.HealthStatus;
import com.ai.gateway.health.HealthIndicator;
import com.ai.gateway.health.HealthResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GeminiHealthIndicator implements HealthIndicator {

    private final GeminiConfig geminiConfig;

    @Override
    public String name() {
        return "Gemini";
    }

    @Override
    public HealthResult check() {

        if (isBlank(geminiConfig.getApiKey())) {
            return down("Gemini API Key is not configured.");
        }

        if (isBlank(geminiConfig.getUrl())) {
            return down("Gemini URL is not configured.");
        }

        if (isBlank(geminiConfig.getModel())) {
            return down("Gemini Model is not configured.");
        }

        return HealthResult.builder()
                .component(name())
                .status(HealthStatus.UP)
                .message("Gemini configuration is valid.")
                .build();
    }

    private HealthResult down(String message) {
        return HealthResult.builder()
                .component(name())
                .status(HealthStatus.DOWN)
                .message(message)
                .build();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
