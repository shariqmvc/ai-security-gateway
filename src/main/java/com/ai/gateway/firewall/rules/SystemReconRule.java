package com.ai.gateway.firewall.rules;

import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.PromptRule;
import com.ai.gateway.firewall.config.FirewallProperties;
import com.ai.gateway.firewall.rulemetadata.RuleEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SystemReconRule implements PromptRule {
    private final FirewallProperties properties;
    private final RuleEvaluator evaluator;

    @Override
    public FirewallResult evaluate(String prompt) {

        return evaluator.evaluate(
                prompt,
                properties.getSystemRecon(),
                "File system access detected.");
    }

    @Override
    public String name() {
        return "File System Access Rule";
    }

    @Override
    public int priority() {
        return 150;
    }
}
