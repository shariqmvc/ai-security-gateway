package com.ai.gateway.firewall.rules;

import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.PromptRule;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PromptInjectionRule implements PromptRule {
    private static final List<String> PATTERNS = List.of(

            "ignore previous instructions",
            "ignore all previous instructions",
            "forget previous instructions",
            "disregard previous instructions",
            "ignore system prompt",
            "ignore safety instructions",
            "bypass restrictions",
            "override instructions",
            "act without limitations",
            "disable safety"

    );

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

        for (String pattern : PATTERNS) {

            if (lower.contains(pattern)) {

                return FirewallResult.builder()
                        .allowed(false)
                        .reason("Prompt Injection Detected")
                        .build();

            }
        }

        return FirewallResult.builder()
                .allowed(true)
                .build();

    }
}
