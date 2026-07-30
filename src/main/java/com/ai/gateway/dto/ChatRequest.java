package com.ai.gateway.dto;

import lombok.*;

@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequest {

   // @NotBlank(message = "Prompt cannot be empty")
    private String prompt;

}