package com.ai.gateway.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class APIResponse<T> {

    private boolean success;

    private String message;

    private T data;

    @Builder.Default
    private Instant timestamp = Instant.now();
}
