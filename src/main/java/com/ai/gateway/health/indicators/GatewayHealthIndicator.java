package com.ai.gateway.health.indicators;

import com.ai.gateway.enums.HealthStatus;
import com.ai.gateway.health.HealthIndicator;
import com.ai.gateway.health.HealthResult;
import org.springframework.stereotype.Component;

@Component
public class GatewayHealthIndicator
        implements HealthIndicator {

    @Override
    public String name() {
        return "Gateway";
    }

    @Override
    public HealthResult check() {

        return HealthResult.builder()
                .component(name())
                .status(HealthStatus.UP)
                .message("Gateway is running.")
                .build();

    }

}
