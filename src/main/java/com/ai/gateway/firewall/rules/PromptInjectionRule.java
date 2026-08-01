package com.ai.gateway.firewall.rules;

import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.PromptRule;
import com.ai.gateway.firewall.config.FirewallProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class PromptInjectionRule implements PromptRule {
    private final FirewallProperties properties;

    @Override
    public String name() {
        return "";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public FirewallResult evaluate(String prompt) {

        String lower = prompt.toLowerCase();

        for (String pattern : properties.getPromptInjection()) {

            if (lower.contains(pattern.toLowerCase())) {

                return FirewallResult.builder()
                        .allowed(false)
                        .reason("Prompt injection detected.")
                        .build();
            }
        }

        return FirewallResult.builder()
                .allowed(true)
                .build();

    }
}
