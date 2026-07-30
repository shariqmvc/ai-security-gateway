package com.ai.gateway.util;

import com.ai.gateway.constants.PIIType;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class TokenGenerator {

    private final Map<PIIType, Integer> counters = new EnumMap<>(PIIType.class);

    public TokenGenerator() {
        for (PIIType type : PIIType.values()) {
            counters.put(type, 1);
        }
    }

    public String nextToken(PIIType type) {

        int count = counters.get(type);

        counters.put(type, count + 1);

        return "{{" + type.name() + "_" + count + "}}";
    }
}
