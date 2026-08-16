package com.ai.gateway.service;

import com.ai.gateway.routing.RoutingStrategyHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class RoutingStrategyOrderTest {

    @Autowired
    private List<RoutingStrategyHandler> strategies;

    @Test
    void shouldLoadRoutingStrategiesInExpectedOrder() {

        assertTrue(strategies.size() >= 4);

        assertEquals(
                "ExplicitProviderRoutingStrategy",
                strategies.get(0)
                        .getClass()
                        .getSimpleName());

        assertEquals(
                "ExplicitModelRoutingStrategy",
                strategies.get(1)
                        .getClass()
                        .getSimpleName());

        assertEquals(
                "PolicyBasedRoutingStrategy",
                strategies.get(2)
                        .getClass()
                        .getSimpleName());

        assertEquals(
                "TenantDefaultRoutingStrategy",
                strategies.get(3)
                        .getClass()
                        .getSimpleName());
    }
}