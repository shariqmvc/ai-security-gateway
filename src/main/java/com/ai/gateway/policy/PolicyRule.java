package com.ai.gateway.policy;

public interface PolicyRule {

    PolicyResult evaluate(String prompt);

}