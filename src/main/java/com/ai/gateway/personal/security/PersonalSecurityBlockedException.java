package com.ai.gateway.personal.security;

import com.ai.gateway.personal.intelligence.PersonalSecurityAssessment;

import java.util.UUID;

/** Raised when the Personal ML firewall explicitly blocks an inference. */
public class PersonalSecurityBlockedException extends RuntimeException {

    private final PersonalSecurityAssessment assessment;
    private final UUID requestId;

    public PersonalSecurityBlockedException(UUID requestId, PersonalSecurityAssessment assessment) {
        super(buildMessage(assessment));
        this.requestId = requestId;
        this.assessment = assessment;
    }

    public UUID requestId() {
        return requestId;
    }

    public PersonalSecurityAssessment assessment() {
        return assessment;
    }

    private static String buildMessage(PersonalSecurityAssessment assessment) {
        if (assessment == null || assessment.labels().isEmpty()) {
            return "Request blocked by AIRouter security firewall. The request did not pass the personal security policy. Please revise the prompt and try again.";
        }
        return "Request blocked by AIRouter security firewall. The request was classified as "
                + String.join(", ", assessment.labels())
                + ". Please revise the prompt and try again.";
    }
}
