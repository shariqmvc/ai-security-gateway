package com.ai.gateway.personal.intelligence;

import com.ai.gateway.personal.security.PersonalFirewallProperties;
import com.ai.gateway.personal.security.firewall.PersonalFirewallClient;
import com.ai.gateway.personal.security.firewall.PersonalFirewallDetection;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Personal pre-provider security intelligence.
 *
 * <p>When the external ML firewall is enabled, this service delegates
 * detection to the standalone AIRouter Firewall service. The legacy
 * deterministic detector remains available when the external firewall is
 * explicitly disabled, which keeps local/unit-test behavior deterministic
 * without loading ML models into the Spring Boot JVM.</p>
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

    private final PersonalFirewallClient firewallClient;
    private final PersonalFirewallProperties firewallProperties;

    public PersonalSecurityIntelligenceService(
            PersonalFirewallClient firewallClient,
            PersonalFirewallProperties firewallProperties) {
        this.firewallClient = firewallClient;
        this.firewallProperties = firewallProperties;
    }

    public PersonalSecurityAssessment assess(String prompt) {
        return assess(UUID.randomUUID(), prompt);
    }

    public PersonalSecurityAssessment assess(UUID requestId, String prompt) {
        if (firewallProperties.isEnabled()) {
            PersonalFirewallDetection detection = firewallClient.detect(requestId, prompt);
            return fromFirewall(detection, prompt);
        }

        return assessLegacy(prompt);
    }

    private PersonalSecurityAssessment fromFirewall(PersonalFirewallDetection detection, String prompt) {
        PersonalSecurityRisk risk = parseRisk(detection.risk());
        boolean maliciousLabel = detection.labels().stream()
                .anyMatch(label -> label != null && !"BENIGN".equalsIgnoreCase(label));
        int score = maliciousLabel
                ? (int) Math.round(Math.max(0.0d, Math.min(1.0d, detection.score())) * 100.0d)
                : 0;
        boolean benignFileAnalysis = detection.labels().stream()
                .anyMatch(label -> "SYSTEM_PROMPT_EXTRACTION".equalsIgnoreCase(label))
                && isBenignFileAnalysisRequest(prompt);
        String decision = benignFileAnalysis ? "ALLOW" : detection.decision();
        if (benignFileAnalysis) {
            risk = PersonalSecurityRisk.LOW;
            score = Math.min(score, 15);
        }

        return new PersonalSecurityAssessment(
                score,
                risk,
                detection.signals(),
                detection.labels(),
                decision,
                detection.model(),
                detection.modelVersion(),
                Math.round(Math.max(0.0d, detection.latencyMs()))
        );
    }

    /**
     * Legitimate attachment-analysis requests must not be confused with
     * requests to reveal AIRouter's own system/developer instructions.
     *
     * <p>This guard is intentionally narrow: it only applies to the
     * SYSTEM_PROMPT_EXTRACTION label and only to a short, simple file-analysis
     * request. Any explicit system/developer prompt extraction language still
     * goes to the normal blocking path.</p>
     */
    static boolean isBenignFileAnalysisRequest(String prompt) {
        if (prompt == null) {
            return false;
        }

        String value = prompt.trim().toLowerCase(Locale.ROOT);
        if (value.length() > 300
                || value.contains("system prompt")
                || value.contains("developer message")
                || value.contains("hidden instruction")
                || value.contains("internal instruction")
                || value.contains("reveal")
                || value.contains("ignore all previous")
                || value.contains("bypass")) {
            return false;
        }

        return value.matches(
                "please\\s+(analyze|summarize|review|inspect|explain|extract)\\s+the\\s+attached\\s+(image|pdf|docx?|xlsx?|pptx?|txt|csv|json|md|zip)\\s*(file|archive)?(\\s+and\\s+its\\s+contents)?\\.?");
    }

    private PersonalSecurityRisk parseRisk(String value) {
        if (value == null) {
            return PersonalSecurityRisk.HIGH;
        }
        try {
            return PersonalSecurityRisk.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return PersonalSecurityRisk.HIGH;
        }
    }

    private PersonalSecurityAssessment assessLegacy(String prompt) {
        String value = prompt == null ? "" : prompt;
        int score = 0;
        List<String> signals = new ArrayList<>();

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
