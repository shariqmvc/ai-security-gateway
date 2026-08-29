package com.ai.gateway.core.provider.gemini.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UsageMetadata {

    private Integer promptTokenCount;

    private Integer candidatesTokenCount;

    private Integer totalTokenCount;

    private Integer thoughtsTokenCount;

}
