package com.ai.gateway.core.provider.gemini.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class GeminiResponse {

    private List<Candidate> candidates;
    private UsageMetadata usageMetadata;

    private String modelVersion;

    private String responseId;

}