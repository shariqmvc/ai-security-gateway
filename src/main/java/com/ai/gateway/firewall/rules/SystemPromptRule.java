package com.ai.gateway.firewall.rules;

import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.PromptRule;
import com.ai.gateway.firewall.config.FirewallProperties;
import com.ai.gateway.firewall.rulemetadata.RuleEvaluator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SystemPromptRule implements PromptRule {

    private final FirewallProperties properties;
    private final RuleEvaluator evaluator;

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

        return evaluator.evaluate(

                prompt,

                properties.getSystemPrompt(),

                "Attempt to access system prompt detected."
        );
    }
}
