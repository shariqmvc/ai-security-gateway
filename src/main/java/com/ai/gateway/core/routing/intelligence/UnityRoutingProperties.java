package com.ai.gateway.core.routing.intelligence;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gateway.routing.unity")
public class UnityRoutingProperties {
    /** Platform capability gate. Defaults to false: Unity is opt-in and not active by default. */
    private boolean enabled = false;
}
