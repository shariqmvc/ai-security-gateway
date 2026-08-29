package com.ai.gateway.core.provider.openai.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OpenAIRequest {

    private String model;

    private Object input;

}
