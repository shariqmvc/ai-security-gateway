package com.ai.gateway.provider.openai.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpenAIInputText {
    @Builder.Default
    private String type = "input_text";
    private String text;
}
