package com.ai.gateway.firewall.service.impl;

import com.ai.gateway.firewall.FirewallResult;
import com.ai.gateway.firewall.PromptRule;
import com.ai.gateway.firewall.service.PromptFireWallService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PromptFireWallServiceImpl implements PromptFireWallService {

    private final List<PromptRule> rules;

    public FirewallResult inspect(String prompt) {

        for (PromptRule rule : rules) {

            FirewallResult result = rule.evaluate(prompt);

            if (!result.isAllowed()) {
                return result;
            }
        }

        return FirewallResult.builder()
                .allowed(true)
                .build();
    }
}
