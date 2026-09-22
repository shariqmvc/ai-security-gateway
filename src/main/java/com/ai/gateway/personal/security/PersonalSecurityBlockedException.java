package com.ai.gateway.personal.security;

import com.ai.gateway.personal.intelligence.PersonalSecurityAssessment;

/** Raised when the Personal ML firewall explicitly blocks an inference. */
public class PersonalSecurityBlockedException extends RuntimeException {

    private final PersonalSecurityAssessment assessment;

    public PersonalSecurityBlockedException(PersonalSecurityAssessment assessment) {
        super(buildMessage(assessment));
        this.assessment = assessment;
    }

    public PersonalSecurityAssessment assessment() {
        return assessment;
    }

    private static String buildMessage(PersonalSecurityAssessment assessment) {
        if (assessment == null) {
            return "Personal security firewall blocked the request.";
        }
        if (!assessment.labels().isEmpty()) {
            return "Personal security firewall blocked the request: "
                    + String.join(", ", assessment.labels());
        }
        return "Personal security firewall blocked the request.";
    }
}
