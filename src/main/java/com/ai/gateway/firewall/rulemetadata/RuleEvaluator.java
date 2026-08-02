package com.ai.gateway.firewall.rulemetadata;

import com.ai.gateway.firewall.FirewallResult;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class RuleEvaluator {

    public FirewallResult evaluate(String prompt,
                                   RuleGroup group,
                                   String reason) {

        if (!group.isEnabled()) {

            return FirewallResult.builder()
                    .allowed(true)
                    .build();
        }

        int score = 0;

        for (FirewallRule rule : group.getRules()) {

            if (Pattern.compile(rule.getRegex())
                    .matcher(prompt)
                    .find()) {

                score += rule.getScore();
            }
        }

        if (score >= group.getThreshold()) {

            return FirewallResult.builder()
                    .allowed(false)
                    .reason(reason)
                    .build();
        }

        return FirewallResult.builder()
                .allowed(true)
                .build();
    }
}
