package com.ai.gateway.personal.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Security mode for Personal Chat.
 *
 * Personal Chat defaults to adaptive security rather than the legacy
 * Business hard-block firewall/policy rules. Hard rules remain available
 * as an explicit deployment-level opt-in.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "personal.security.chat")
public class PersonalChatSecurityProperties {

    /**
     * When true, the legacy firewall and policy hard-block rules also apply
     * to Personal Chat. Default is false because Personal Chat is a general
     * purpose AI workspace and must support normal coding/technical prompts.
     */
    private boolean hardRulesEnabled = false;
}
