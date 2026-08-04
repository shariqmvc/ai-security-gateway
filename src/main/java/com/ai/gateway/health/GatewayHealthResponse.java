package com.ai.gateway.health;

import com.ai.gateway.enums.HealthStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GatewayHealthResponse {

    private HealthStatus overallStatus;

    private LocalDateTime timestamp;

    private List<HealthResult> components;

}
