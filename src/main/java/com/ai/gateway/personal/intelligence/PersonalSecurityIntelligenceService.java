package com.ai.gateway.personal.intelligence;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Lightweight, deterministic Personal security intelligence.
 *
 * This is a pre-provider signal layer. It does not replace the firewall,
 * PII masking, quota, rate limiting, or abuse guard. It produces an explainable
 * score and can deny clearly high-risk prompts before provider invocation.
 */
@Service
public class PersonalSecurityIntelligenceService {

    private static final Pattern[] SUSPICIOUS_PATTERNS = {
            Pattern.compile("ignore\\s+(?:all\\s+)?previous\\s+instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal\\s+(the\\s+)?system\\s+prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("developer\\s+message", Pattern.CASE_INSENSITIVE),
            Pattern.compile("bypass\\s+(the\\s+)?security", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disable\\s+(the\\s+)?safety", Pattern.CASE_INSENSITIVE),
            Pattern.compile("exfiltrat(e|ion)", Pattern.CASE_INSENSITIVE)
    };

    public PersonalSecurityAssessment assess(String prompt) {
        String value = prompt == null ? "" : prompt;
        int score = 0;
        List<String> signals = new java.util.ArrayList<>();

        if (value.length() > 20_000) {
            score += 15;
            signals.add("large-prompt");
        }
        if (value.length() > 50_000) {
            score += 20;
            signals.add("very-large-prompt");
        }

        for (Pattern pattern : SUSPICIOUS_PATTERNS) {
            if (pattern.matcher(value).find()) {
                score += 20;
                signals.add("suspicious-pattern:" + pattern.pattern());
            }
        }

        if (countRepeatedInstructionMarkers(value) >= 3) {
            score += 15;
            signals.add("repeated-instruction-markers");
        }

        score = Math.min(100, score);
        PersonalSecurityRisk risk =
                score >= 70 ? PersonalSecurityRisk.HIGH
                        : score >= 35 ? PersonalSecurityRisk.MEDIUM
                        : PersonalSecurityRisk.LOW;

        return new PersonalSecurityAssessment(score, risk, List.copyOf(signals));
    }

    private int countRepeatedInstructionMarkers(String prompt) {
        String lower = prompt.toLowerCase(Locale.ROOT);
        int count = 0;
        for (String marker : List.of(
                "system:", "developer:", "assistant:", "ignore:", "override:")) {
            int from = 0;
            while ((from = lower.indexOf(marker, from)) >= 0) {
                count++;
                from += marker.length();
            }
        }
        return count;
    }
}
