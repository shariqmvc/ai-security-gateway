package com.ai.gateway.dto;

import lombok.*;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequest {

   // @NotBlank(message = "Prompt cannot be empty")
    private String prompt;

}