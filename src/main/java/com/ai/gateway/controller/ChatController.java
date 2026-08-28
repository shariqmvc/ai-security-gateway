package com.ai.gateway.controller;

import com.ai.gateway.common.APIResponse;
import com.ai.gateway.dto.ChatRequest;
import com.ai.gateway.dto.ChatResponse;
import com.ai.gateway.service.GatewayService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final GatewayService gatewayService;
    private final ObjectMapper objectMapper;


    @PostMapping(
            value = "/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> stream(
            @Valid @RequestBody ChatRequest request) {

        StreamingResponseBody body = outputStream -> {
            gatewayService.stream(
                    request,
                    event -> {
                        try {
                            String payload =
                                    objectMapper.writeValueAsString(event);

                            outputStream.write(
                                    ("event: " + event.getType() + "\n"
                                            + "data: " + payload + "\n\n")
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            outputStream.flush();
                        } catch (java.io.IOException ex) {
                            throw new com.ai.gateway.service.StreamClientDisconnectedException(ex);
                        }
                    });
        };

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("X-Accel-Buffering", "no")
                .body(body);
    }

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
