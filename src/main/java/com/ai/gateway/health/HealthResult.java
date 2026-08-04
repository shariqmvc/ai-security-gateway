package com.ai.gateway.health;

import com.ai.gateway.enums.HealthStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class HealthResult {

    private String component;

    private HealthStatus status;

    private String message;

}
