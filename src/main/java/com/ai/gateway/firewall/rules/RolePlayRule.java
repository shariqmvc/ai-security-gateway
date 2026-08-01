package com.ai.gateway.firewall.rules;

import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.PromptRule;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RolePlayRule implements PromptRule {

    private static final List<String> PATTERNS = List.of(

            "act as",
            "pretend to be",
            "you are now",
            "developer mode",
            "dan",
            "do anything now",
            "evil assistant",
            "unrestricted ai",
            "ignore your rules",
            "simulate"

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
                        .reason("Role-play jailbreak detected.")
                        .build();

            }
        }

        return FirewallResult.builder()
                .allowed(true)
                .build();
    }
}
