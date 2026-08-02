package com.ai.gateway.firewall.rules;

import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.PromptRule;
import com.ai.gateway.firewall.config.FirewallProperties;
import com.ai.gateway.firewall.rulemetadata.RuleEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JailBreakRule implements PromptRule {

    private final FirewallProperties properties;
    private final RuleEvaluator evaluator;

    @Override
    public String name() {
        return "Jailbreak Rule";
    }

    @Override
    public int priority() {
        return 20;
    }

    @Override
    public FirewallResult evaluate(String prompt) {

        return evaluator.evaluate(
                prompt,
                properties.getJailbreak(),
                "Jailbreak attempt detected."
        );
    }
}
