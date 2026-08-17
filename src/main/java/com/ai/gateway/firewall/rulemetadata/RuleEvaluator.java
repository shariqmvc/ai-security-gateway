package com.ai.gateway.firewall.rulemetadata;

import com.ai.gateway.firewall.FirewallResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

@Component
public class RuleEvaluator {

    private final ConcurrentMap<String, Pattern> compiledPatterns = new ConcurrentHashMap<>();

    public FirewallResult evaluate(String prompt, RuleGroup group, String reason) {
        if (!group.isEnabled()) {
            return FirewallResult.builder().allowed(true).build();
        }

        int score = 0;
        if (group.getRules() == null || group.getRules().isEmpty()) {
            return FirewallResult.builder().allowed(true).build();
        }

        for (FirewallRule rule : group.getRules()) {
            if (rule == null || rule.getRegex() == null || rule.getRegex().isBlank()) continue;
            Pattern pattern = compiledPatterns.computeIfAbsent(rule.getRegex(), Pattern::compile);
            if (pattern.matcher(prompt).find()) {
                score += rule.getScore();
                if (score >= group.getThreshold()) {
                    return FirewallResult.builder()
                            .allowed(false)
                            .reason(reason)
                            .build();
                }
            }
        }

        return FirewallResult.builder().allowed(true).build();
    }
}
