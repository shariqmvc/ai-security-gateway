package com.ai.gateway.personal;

import com.ai.gateway.entitlement.enums.Feature;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumSet;
import java.util.Set;

@Getter
@Setter
@ConfigurationProperties(prefix = "alroute.personal.features")
public class PersonalFeatureEntitlementProperties {
    private Set<Feature> enabled = EnumSet.of(
            Feature.OPENAI, Feature.GEMINI, Feature.CLAUDE, Feature.OLLAMA,
            Feature.CHAT, Feature.STREAMING, Feature.EMBEDDING,
            Feature.PROMPT_FIREWALL, Feature.POLICY_ENGINE, Feature.PII_DETECTION,
            Feature.AUDIT, Feature.METRICS, Feature.TOKEN_ANALYTICS, Feature.COST_ANALYTICS,
            Feature.RATE_LIMITING, Feature.QUOTA,
            Feature.RAG, Feature.MCP, Feature.EXTENSIVE_RESEARCH,
            Feature.DASHBOARD
    );
}
