package com.ai.gateway.entitlement.enums;

public enum Feature {
    // AI Providers
    OPENAI,
    GEMINI,
    OLLAMA,
    CLAUDE,

    // Core Gateway
    CHAT,
    STREAMING,
    EMBEDDING,

    // Security
    PROMPT_FIREWALL,
    POLICY_ENGINE,
    PII_DETECTION,

    // Governance
    AUDIT,
    METRICS,
    TOKEN_ANALYTICS,
    COST_ANALYTICS,

    // Enterprise
    RATE_LIMITING,
    QUOTA,
    BUDGET,

    // Advanced AI
    RAG,
    MCP,
    EXTENSIVE_RESEARCH,

    // Administration
    DASHBOARD,
    ADMIN_API
}
