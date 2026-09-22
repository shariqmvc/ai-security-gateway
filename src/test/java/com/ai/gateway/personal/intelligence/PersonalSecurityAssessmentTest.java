package com.ai.gateway.personal.intelligence;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PersonalSecurityAssessmentTest {

    @Test
    void mlJailbreakLabelIsBlockingSignal() {
        var assessment = new PersonalSecurityAssessment(
                97,
                PersonalSecurityRisk.HIGH,
                List.of(),
                List.of("JAILBREAK"),
                "BLOCK",
                "deberta-v3-small",
                "airouter-firewall-v2.1",
                112);

        assertTrue(assessment.promptInjectionDetected());
        assertTrue(assessment.shouldBlock());
    }
}
