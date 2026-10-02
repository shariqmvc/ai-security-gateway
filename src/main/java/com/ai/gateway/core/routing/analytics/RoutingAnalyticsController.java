package com.ai.gateway.core.routing.analytics;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-operations view of routing decision telemetry.
 *
 * This endpoint exposes aggregated in-process routing counters only; it does
 * not expose request prompts, credentials, or tenant-private payload data.
 */
@RestController
@RequestMapping("/api/routing/analytics")
@PreAuthorize("hasAnyRole('PLATFORM_OWNER','PLATFORM_ADMIN','PLATFORM_OPERATIONS')")
@RequiredArgsConstructor
public class RoutingAnalyticsController {

    private final RoutingAnalyticsService analyticsService;

    @GetMapping
    public RoutingAnalytics analytics() {
        return analyticsService.getAnalytics();
    }
}
