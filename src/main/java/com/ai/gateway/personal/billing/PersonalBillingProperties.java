package com.ai.gateway.personal.billing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashSet;
import java.util.Set;

@Getter @Setter
@ConfigurationProperties(prefix = "alroute.personal.billing")
public class PersonalBillingProperties {
    private Set<String> freeModels = new HashSet<>();
}
