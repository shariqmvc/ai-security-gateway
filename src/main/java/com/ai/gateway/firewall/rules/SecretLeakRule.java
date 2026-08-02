package com.ai.gateway.firewall.rules;

import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.PromptRule;
import com.ai.gateway.firewall.config.FirewallProperties;
import com.ai.gateway.firewall.rulemetadata.RuleEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecretLeakRule implements PromptRule {

    private final FirewallProperties properties;
    private final RuleEvaluator evaluator;

    @Override
    public FirewallResult evaluate(String prompt) {

        return evaluator.evaluate(
                prompt,
                properties.getSecretLeak(),
                "Secret extraction detected.");
    }

    @Override
    public String name() {
        return "Secret Leak Rule";
    }

    @Override
    public int priority() {
        return 200;
    }
}
