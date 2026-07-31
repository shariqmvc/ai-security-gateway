package com.ai.gateway.dto;

import com.ai.gateway.enums.Provider;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AIRequest {

    private Provider provider;

    private String model;

    private String prompt;

}
