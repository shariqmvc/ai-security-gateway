package com.ai.gateway.core.provider.openai.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpenAIInputAudio {
    @Builder.Default
    private String type = "input_audio";
    @com.fasterxml.jackson.annotation.JsonProperty("input_audio")
    private InputAudio inputAudio;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InputAudio {
        private String data;
        private String format;
    }
}
