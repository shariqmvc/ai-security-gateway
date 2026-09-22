package com.ai.gateway.personal.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration for the external Personal ML security firewall.
 *
 * <p>The firewall is deliberately kept outside the Spring Boot JVM because
 * the detector is a PyTorch/Transformers workload. Personal Chat fails closed
 * when the security service is unavailable unless fail-open is explicitly
 * enabled for a development deployment.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "personal.security.firewall")
public class PersonalFirewallProperties {

    private boolean enabled = true;
    private String baseUrl = "http://localhost:8090";
    private int connectTimeoutMs = 500;
    private int readTimeoutMs = 1500;
    private boolean failOpen = false;
    private int maxTextLength = 20_000;
}
