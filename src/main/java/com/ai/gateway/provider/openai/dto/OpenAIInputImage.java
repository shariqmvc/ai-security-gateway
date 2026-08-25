package com.ai.gateway.provider.openai.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpenAIInputImage {
    @Builder.Default
    private String type = "input_image";
    @com.fasterxml.jackson.annotation.JsonProperty("image_url")
    private String imageUrl;
    private String detail;
}
