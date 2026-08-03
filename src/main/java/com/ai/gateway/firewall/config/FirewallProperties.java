package com.ai.gateway.firewall.config;

import com.ai.gateway.firewall.rulemetadata.RuleGroup;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "firewall")
public class FirewallProperties {

    private RuleGroup systemPrompt;

    private RuleGroup jailbreak;

    private RuleGroup roleOverride;

    private RuleGroup promptInjection;

    private RuleGroup secretLeak;

    private RuleGroup dataExfiltration;

    private RuleGroup filesystemAccess;

    private RuleGroup systemRecon;

    private RuleGroup commandExecution;

}
