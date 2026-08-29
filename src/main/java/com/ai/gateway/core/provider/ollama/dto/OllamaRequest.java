package com.ai.gateway.core.provider.ollama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OllamaRequest {

    private String model;

    private List<OllamaMessage> messages;

    @Builder.Default
    private boolean stream = false;

    private OllamaOptions options;

    /** Ollama keeps the loaded model in memory for this duration when set. */
    @JsonProperty("keep_alive")
    private String keepAlive;
}