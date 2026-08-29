package com.ai.gateway.core.routing.selection;

/**
 * Defines the terminal selection shape produced by the routing optimizer.
 */
public enum RoutingSelectionMode {
    /** Select exactly one winner. */
    SINGLE,

    /** Select the best N eligible candidates. */
    TOP_N,

    /** Select a primary candidate and an explicit escalation candidate. */
    PRIMARY_ESCALATION
}
