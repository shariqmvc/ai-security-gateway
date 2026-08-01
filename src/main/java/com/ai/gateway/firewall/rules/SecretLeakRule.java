package com.ai.gateway.firewall.rules;

import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.PromptRule;
import org.springframework.stereotype.Component;

@Component
public class SecretLeakRule implements PromptRule {

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

        if (lower.contains("api key")
                || lower.contains("password")
                || lower.contains("secret")
                || lower.contains("access token")
                || lower.contains("private key")) {

            return FirewallResult.builder()
                    .allowed(false)
                    .reason("Secret extraction attempt detected.")
                    .build();
        }

        return FirewallResult.builder()
                .allowed(true)
                .build();
    }
}
