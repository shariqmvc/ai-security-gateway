package com.ai.gateway.util;

import java.util.regex.Pattern;

public class RegexUtil {
    private RegexUtil() {
    }

    /**
     * RFC compliant enough for MVP
     */
    public static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    );

    /**
     * Indian mobile numbers
     */
    public static final Pattern PHONE_PATTERN = Pattern.compile(
            "\\b(?:\\+91[-\\s]?)?[6-9]\\d{9}\\b"
    );

    /**
     * Credit card (13-19 digits)
     */
    public static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:\\d[ -]*?){13,19}\\b"
    );
}
