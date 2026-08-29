package com.ai.gateway.core.provider.gemini.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidate {

    private CandidateContent content;
}
