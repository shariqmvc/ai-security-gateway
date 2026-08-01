package com.ai.gateway.firewall.rules;

import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.PromptRule;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataExfiltrationRule implements PromptRule {

    private static final List<String> PATTERNS = List.of(

            "dump database",
            "dump all records",
            "customer data",
            "employee data",
            "confidential",
            "internal document",
            "private document",
            "export all data",
            "show passwords",
            "show api keys",
            "credit card",
            "social security number",
            "ssn",
            "reveal memory",
            "list all users"

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
                        .reason("Possible data exfiltration attempt detected.")
                        .build();

            }
        }

        return FirewallResult.builder()
                .allowed(true)
                .build();
    }
}
