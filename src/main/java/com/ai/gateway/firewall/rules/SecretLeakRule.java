package com.ai.gateway.firewall.rules;

import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.PromptRule;
import com.ai.gateway.firewall.config.FirewallProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecretLeakRule implements PromptRule {

    private final FirewallProperties properties;

    @Override
    public FirewallResult evaluate(String prompt) {

        String lower = prompt.toLowerCase();

        for (String pattern : properties.getSecretLeak()) {

            if (lower.contains(pattern.toLowerCase())) {

                return FirewallResult.builder()
                        .allowed(false)
                        .reason("Attempt to obtain secrets detected.")
                        .build();
            }
        }

        return FirewallResult.builder()
                .allowed(true)
                .build();
    }

    @Override
    public String name() {
        return "Secret Leak Rule";
    }

    @Override
    public int priority() {
        return 200;
    }
}
