package com.ai.gateway.provider.gemini.dto;

import lombok.*;

import java.util.List;
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateContent {

    private List<GeminiPart> parts;

}
