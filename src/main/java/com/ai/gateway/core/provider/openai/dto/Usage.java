package com.ai.gateway.core.provider.openai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class Usage {

    @JsonProperty("input_tokens")
    private Integer inputTokens;

    @JsonProperty("output_tokens")
    private Integer outputTokens;

    @JsonProperty("total_tokens")
    private Integer totalTokens;

}
