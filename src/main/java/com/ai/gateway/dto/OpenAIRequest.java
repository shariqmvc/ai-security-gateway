package com.ai.gateway.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OpenAIRequest {

    private String model;

    private String input;

}
