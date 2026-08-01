package com.ai.gateway.firewall.rules;

import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.PromptRule;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SystemPromptRule implements PromptRule {

    private static final List<String> PATTERNS = List.of(

            "system prompt",
            "hidden prompt",
            "initial instructions",
            "developer instructions",
            "show system prompt",
            "reveal system prompt",
            "display system prompt",
            "internal prompt",
            "prompt template",
            "hidden instructions"

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
                        .reason("Attempt to access system prompt detected.")
                        .build();

            }
        }

        return FirewallResult.builder()
                .allowed(true)
                .build();
    }
}
