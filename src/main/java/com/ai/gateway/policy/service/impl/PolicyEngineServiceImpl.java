package com.ai.gateway.policy.service.impl;

import com.ai.gateway.policy.PolicyResult;
import com.ai.gateway.policy.PolicyRule;
import com.ai.gateway.policy.service.PolicyEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyEngineServiceImpl implements PolicyEngineService {

    private final List<PolicyRule> rules;

    @Override
    public PolicyResult evaluate(String prompt) {
        for (PolicyRule rule : rules) {

            PolicyResult result = rule.evaluate(prompt);

            if (!result.isAllowed()) {
                return result;
            }

        }

        return PolicyResult.builder()
                .allowed(true)
                .build();
    }
}
