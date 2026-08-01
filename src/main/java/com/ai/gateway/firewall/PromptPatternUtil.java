package com.ai.gateway.firewall;

import java.util.List;

public final class PromptPatternUtil {

    private PromptPatternUtil() {
    }

    public static boolean containsAny(String prompt,
                                      List<String> patterns) {

        if (prompt == null || prompt.isBlank()) {
            return false;
        }

        String lower = prompt.toLowerCase();

        return patterns.stream()
                .anyMatch(lower::contains);
    }

}