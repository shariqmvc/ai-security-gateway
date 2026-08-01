package com.ai.gateway.firewall.rules;

import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.PromptRule;
import com.ai.gateway.firewall.config.FirewallProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class DataExfiltrationRule implements PromptRule {

    private final FirewallProperties properties;

    @Override
    public FirewallResult evaluate(String prompt) {

        String lower = prompt.toLowerCase();

        for (String pattern : properties.getDataExfiltration()) {

            if (lower.contains(pattern.toLowerCase())) {

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

    @Override
    public String name() {
        return "Data Exfiltration Rule";
    }

    @Override
    public int priority() {
        return 150;
    }
}
