package com.ai.gateway.health.indicators;

import com.ai.gateway.config.OpenAIConfig;
import com.ai.gateway.enums.HealthStatus;
import com.ai.gateway.health.HealthIndicator;
import com.ai.gateway.health.HealthResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OpenAIHealthIndicator implements HealthIndicator {

    private final OpenAIConfig openAIConfig;

    @Override
    public String name() {
        return "OpenAI";
    }

    @Override
    public HealthResult check() {

        if (isBlank(openAIConfig.getApiKey())) {

            return down("OpenAI API Key is not configured.");

        }

        if (isBlank(openAIConfig.getUrl())) {

            return down("OpenAI URL is not configured.");

        }

        if (isBlank(openAIConfig.getModel())) {

            return down("OpenAI Model is not configured.");

        }

        return HealthResult.builder()
                .component(name())
                .status(HealthStatus.UP)
                .message("OpenAI configuration is valid.")
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
