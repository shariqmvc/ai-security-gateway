package com.ai.gateway.provider.openai.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OpenAIInputContent {
    private String role;
    private java.util.List<Object> content;
}
