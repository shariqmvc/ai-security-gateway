package com.ai.gateway.provider.ollama.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OllamaMessage {

    private String role;

    private String content;
}
