package com.ai.gateway.firewall.rules;

import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.PromptRule;
import org.springframework.stereotype.Component;

@Component
public class JailBreakRule implements PromptRule {

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

        if (lower.contains("ignore previous instructions")) {

            return FirewallResult.builder()
                    .allowed(false)
                    .reason("Prompt injection detected.")
                    .build();
        }

        return FirewallResult.builder()
                .allowed(true)
                .build();
    }
}
