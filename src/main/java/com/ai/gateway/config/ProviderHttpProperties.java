package com.ai.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Shared outbound provider HTTP timeouts. These bound provider calls so a
 * degraded provider cannot hold an interactive gateway request indefinitely.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gateway.provider.http")
public class ProviderHttpProperties {

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(30);
}
