package com.ai.gateway.core.routing.telemetry;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Platform-only routing execution telemetry.
 *
 * <p>No prompt, response content, credentials or tenant-private payloads are
 * returned. The endpoint exposes bounded operational metadata for LLMOps.</p>
 */
@RestController
@RequestMapping("/api/routing/telemetry")
@PreAuthorize("hasAnyRole('PLATFORM_OWNER','PLATFORM_ADMIN','PLATFORM_OPERATIONS')")
@RequiredArgsConstructor
public class RoutingTelemetryController {

    private final RoutingTelemetryService telemetryService;

    @GetMapping
    public RoutingTelemetrySnapshot telemetry() {
        return telemetryService.snapshot();
    }
}
