package com.ai.gateway.firewall.rules;

import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.PromptRule;
import com.ai.gateway.firewall.config.FirewallProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SystemPromptRule implements PromptRule {

    private final FirewallProperties properties;

    @Override
    public String name() {
        return "System Prompt Rule";
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public FirewallResult evaluate(String prompt) {

        String lower = prompt.toLowerCase();

        for (String pattern : properties.getSystemPrompt()) {

            if (lower.contains(pattern.toLowerCase())) {

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
