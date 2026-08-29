package com.ai.gateway.personal.quota.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Personal v1 quota policy configuration.
 *
 * <p>These values define the Personal quota boundary only. Enforcement is
 * intentionally implemented in a later P10 step; this class establishes the
 * policy contract without coupling Personal accounts to enterprise tenant
 * entitlements.</p>
 *
 * <p>A value of {@code 0} means the corresponding limit is not configured yet.
 * This is intentional until the Personal v1 commercial limits are frozen.</p>
 */
@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "gateway.personal.quota")
public class PersonalQuotaProperties {

    private boolean enabled = true;

    private long requestsPerMinute = 0L;

    private long requestsPerDay = 0L;

    private long monthlyTokenQuota = 0L;

    private long maxInputTokens = 0L;

    private long maxOutputTokens = 0L;

    private long maxConcurrentRequests = 0L;
}
