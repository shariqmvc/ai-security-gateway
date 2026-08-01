package com.ai.gateway.policy.rules;

import com.ai.gateway.policy.PolicyResult;
import com.ai.gateway.policy.PolicyRule;
import com.ai.gateway.policy.config.PolicyProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class NoCodeGenerationRule implements PolicyRule {

    private final PolicyProperties properties;

    @Override
    public PolicyResult evaluate(String prompt) {

        if (!properties.getCodeGeneration().isEnabled()) {

            return PolicyResult.builder()
                    .allowed(true)
                    .build();

        }

        String lower = prompt.toLowerCase();

        for (String pattern : properties
                .getCodeGeneration()
                .getPatterns()) {

            if (lower.contains(pattern.toLowerCase())) {

                return PolicyResult.builder()
                        .allowed(false)
                        .reason("Code generation is not permitted.")
                        .build();

            }

        }

        return PolicyResult.builder()
                .allowed(true)
                .build();
    }
}
