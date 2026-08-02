package com.ai.gateway.firewall.rulemetadata;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FirewallRule {

    private String id;

    private String severity;

    private int score;

    private String regex;

}
