package com.ai.gateway.provider.ollama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Runtime generation controls sent to Ollama.
 *
 * <p>Keeping these values explicit makes latency/capacity behavior
 * observable and configurable without changing the provider contract.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OllamaOptions {

    @JsonProperty("num_ctx")
    private Integer numCtx;

    @JsonProperty("num_predict")
    private Integer numPredict;
}
