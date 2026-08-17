package com.ai.gateway.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@PreAuthorize("hasAnyRole('PLATFORM_OWNER','PLATFORM_ADMIN','PLATFORM_OPERATIONS')")
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final GatewayMetricsService metricsService;

    @GetMapping
    public GatewayMetrics metrics() {

        return metricsService.getMetrics();

    }

}
