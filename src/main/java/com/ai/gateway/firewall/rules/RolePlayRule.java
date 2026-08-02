package com.ai.gateway.firewall.rules;

import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.PromptRule;
import com.ai.gateway.firewall.config.FirewallProperties;
import com.ai.gateway.firewall.rulemetadata.RuleEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RolePlayRule implements PromptRule {
    private final FirewallProperties properties;
    private final RuleEvaluator evaluator;

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

        return evaluator.evaluate(
                prompt,
                properties.getRoleOverride(),
                "Role override attempt detected.");

    }
}
