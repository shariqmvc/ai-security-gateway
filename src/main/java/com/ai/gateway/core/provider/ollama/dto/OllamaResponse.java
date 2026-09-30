package com.ai.gateway.core.provider.ollama.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OllamaResponse {

    private OllamaMessage message;

    private Boolean done;

    @JsonProperty("done_reason")
    private String doneReason;

    @JsonProperty("total_duration")
    private Long totalDuration;

    @JsonProperty("load_duration")
    private Long loadDuration;

    @JsonProperty("prompt_eval_count")
    private Integer promptEvalCount;

    @JsonProperty("prompt_eval_duration")
    private Long promptEvalDuration;

    @JsonProperty("eval_count")
    private Integer evalCount;

    @JsonProperty("eval_duration")
    private Long evalDuration;

    public OllamaResponse(
            OllamaMessage message,
            Long totalDuration,
            Long loadDuration,
            Integer promptEvalCount,
            Long promptEvalDuration,
            Integer evalCount,
            Long evalDuration) {
        this.message = message;
        this.done = null;
        this.doneReason = null;
        this.totalDuration = totalDuration;
        this.loadDuration = loadDuration;
        this.promptEvalCount = promptEvalCount;
        this.promptEvalDuration = promptEvalDuration;
        this.evalCount = evalCount;
        this.evalDuration = evalDuration;
    }
}
