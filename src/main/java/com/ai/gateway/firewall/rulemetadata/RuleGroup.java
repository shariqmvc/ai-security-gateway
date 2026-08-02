package com.ai.gateway.firewall.rulemetadata;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class RuleGroup {

    private boolean enabled;

    private int threshold;

    private String action;

    private List<FirewallRule> rules;

}
