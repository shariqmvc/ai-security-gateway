package com.ai.gateway.policy.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "policy")
public class PolicyProperties {

    private Rule codeGeneration = new Rule();

    private Rule piiAccess = new Rule();

    private Rule financialAdvice = new Rule();

    private Rule medicalAdvice = new Rule();

    @Getter
    @Setter
    public static class Rule {

        private boolean enabled;

        private List<String> patterns;

    }

}
