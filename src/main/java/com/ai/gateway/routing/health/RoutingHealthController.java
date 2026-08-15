package com.ai.gateway.routing.health;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/routing/health")
@RequiredArgsConstructor
public class RoutingHealthController {

    private final RoutingHealthService healthService;

    @GetMapping
    public List<RoutingHealthSnapshot> health() {
        return healthService.snapshots();
    }

    @GetMapping("/{provider}/{model}")
    public RoutingHealthSnapshot health(
            @PathVariable String provider,
            @PathVariable String model) {
        try {
            return healthService.snapshot(
                    new com.ai.gateway.routing.engine.RoutingCandidate(
                            com.ai.gateway.enums.Provider.valueOf(provider.toUpperCase()),
                            model));
        } catch (IllegalArgumentException ex) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Unknown provider: " + provider);
        }
    }
}
