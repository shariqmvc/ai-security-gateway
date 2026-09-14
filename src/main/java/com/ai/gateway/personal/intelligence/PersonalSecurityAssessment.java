package com.ai.gateway.personal.intelligence;

import java.util.List;

public record PersonalSecurityAssessment(
        int score,
        PersonalSecurityRisk risk,
        List<String> signals) {}
