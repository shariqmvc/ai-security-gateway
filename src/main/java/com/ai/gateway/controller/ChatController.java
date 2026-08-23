package com.ai.gateway.controller;

import com.ai.gateway.common.APIResponse;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.dto.ChatResponse;
import com.ai.gateway.service.GatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final GatewayService gatewayService;

    @PostMapping
    public ResponseEntity<APIResponse<ChatResponse>> chat(
             @Valid @RequestBody ChatRequest request) {

        ChatResponse response = gatewayService.process(request);

        return ResponseEntity.ok(
                APIResponse.<ChatResponse>builder()
                        .success(true)
                        .message("Response generated successfully.")
                        .data(response)
                        .build()
        );
    }
}
