package com.ai.gateway.provider.ollama.dto;

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
}