package com.ai.gateway.firewall;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FirewallResult {

    private boolean allowed;

    private String reason;

}
