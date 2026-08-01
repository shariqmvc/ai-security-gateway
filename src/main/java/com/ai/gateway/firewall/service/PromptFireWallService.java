package com.ai.gateway.firewall.service;

import com.ai.gateway.firewall.FirewallResult;

public interface PromptFireWallService {
    FirewallResult inspect(String prompt);
}
