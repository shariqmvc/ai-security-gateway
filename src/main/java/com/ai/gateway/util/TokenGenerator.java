package com.ai.gateway.util;

import com.ai.gateway.enums.PIIType;

import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.Map;

public class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String nextToken(PIIType type) {

        byte[] bytes = new byte[4]; // 8 hex characters
        RANDOM.nextBytes(bytes);

        StringBuilder sb = new StringBuilder();

        sb.append("<PII_");
        sb.append(type.name());
        sb.append("_");

        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }

        sb.append(">");

        return sb.toString();
    }
}
