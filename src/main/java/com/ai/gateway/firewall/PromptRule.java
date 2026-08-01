package com.ai.gateway.firewall;

public interface PromptRule {
    String name();

    int priority();
    FirewallResult evaluate(String prompt);
}
