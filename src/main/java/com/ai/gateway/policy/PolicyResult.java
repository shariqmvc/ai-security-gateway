package com.ai.gateway.policy;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PolicyResult {

    private boolean allowed;

    private String reason;

}