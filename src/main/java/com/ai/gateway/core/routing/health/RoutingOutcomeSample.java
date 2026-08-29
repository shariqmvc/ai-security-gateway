package com.ai.gateway.core.routing.health;

/** Product-neutral routing observation used by the core health engine. */
public record RoutingOutcomeSample(long latencyMs, boolean success) {
}
