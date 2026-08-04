package com.ai.gateway.health;

public interface HealthIndicator {

    String name();

    HealthResult check();

}