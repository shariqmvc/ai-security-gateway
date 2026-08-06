package com.ai.gateway.dto;

import com.ai.gateway.enums.Provider;
import lombok.*;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequest {

   // @NotBlank(message = "Prompt cannot be empty")
    private String prompt;
    private Provider provider;
    private String model;

}