package com.ai.gateway.util;

import com.ai.gateway.enums.PIIType;

import java.security.SecureRandom;

public class TokenGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    public String nextToken(PIIType type) {
        byte[] bytes = new byte[4];
        RANDOM.nextBytes(bytes);

        StringBuilder sb = new StringBuilder(16);
        sb.append("<PII_").append(type.name()).append('_');
        for (byte b : bytes) {
            int value = b & 0xFF;
            sb.append(HEX[value >>> 4]).append(HEX[value & 0x0F]);
        }
        return sb.append('>').toString();
    }
}
