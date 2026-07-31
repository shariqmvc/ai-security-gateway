package com.ai.gateway.provider.gemini.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidate {

    private CandidateContent content;
}
