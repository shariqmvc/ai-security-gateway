package com.ai.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @NotBlank(message = "Chat message role cannot be empty")
    private String role;

    @NotBlank(message = "Chat message content cannot be empty")
    private String content;
}
