package com.ai.gateway.core.provider.gemini.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidate {

    private CandidateContent content;

    private String finishReason;

    private String finishMessage;

    public Candidate(CandidateContent content, String finishReason) {
        this(content, finishReason, null);
    }

    public Candidate(CandidateContent content) {
        this(content, null, null);
    }
}
