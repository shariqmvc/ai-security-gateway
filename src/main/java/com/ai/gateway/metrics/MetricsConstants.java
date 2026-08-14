package com.ai.gateway.metrics;

public final class MetricsConstants {

    private MetricsConstants() {
    }

    public static final String TOTAL_REQUESTS = "TOTAL_REQUESTS";
    public static final String SUCCESSFUL_REQUESTS = "SUCCESSFUL_REQUESTS";
    public static final String FAILED_REQUESTS = "FAILED_REQUESTS";

    public static final String FIREWALL_BLOCKED = "FIREWALL_BLOCKED";
    public static final String POLICY_BLOCKED = "POLICY_BLOCKED";

    public static final String ACCESS_DENIED = "ACCESS_DENIED";

    public static final String RATE_LIMIT_ALLOWED = "RATE_LIMIT_ALLOWED";
    public static final String RATE_LIMIT_BLOCKED = "RATE_LIMIT_BLOCKED";

    public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";
    public static final String BUDGET_EXCEEDED = "BUDGET_EXCEEDED";

    public static final String OPENAI_REQUESTS = "OPENAI_REQUESTS";
    public static final String GEMINI_REQUESTS = "GEMINI_REQUESTS";
    public static final String CLAUDE_REQUESTS = "CLAUDE_REQUESTS";
    public static final String OLLAMA_REQUESTS = "OLLAMA_REQUESTS";
    public static final String ROUTING_DECISIONS =
            "gateway.routing.decisions";

    public static final String ROUTING_EXPLICIT_PROVIDER =
            "ROUTING_EXPLICIT_PROVIDER";

    public static final String ROUTING_EXPLICIT_MODEL =
            "ROUTING_EXPLICIT_MODEL";

    public static final String ROUTING_TENANT_DEFAULT =
            "ROUTING_TENANT_DEFAULT";

    public static final String ROUTING_FAILOVER_ATTEMPTS =
            "ROUTING_FAILOVER_ATTEMPTS";

    public static final String ROUTING_FAILOVER_SUCCESS =
            "ROUTING_FAILOVER_SUCCESS";
    public static final String ROUTING_POLICY_BASED =
            "ROUTING_POLICY_BASED";


}
