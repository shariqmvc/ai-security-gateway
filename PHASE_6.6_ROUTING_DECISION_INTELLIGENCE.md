# AegisAI Phase 6.6 — Routing Decision Intelligence

## 6.6.1 Routing Decision Context

Adds a structured `RoutingDecisionContext` carrying tenant context, required capabilities, Unity/Extensive Research state, execution role, and deterministic routing priority. `ChatRequest` exposes optional `requiredCapabilities`, `extensiveResearch`, `executionRole`, and `routingPriority` fields without changing the existing provider/model contract.

## 6.6.2 Capability Matching

Adds `CandidateCapabilityMatcher` and filters eligible candidates against required model capabilities before scoring. Capability matching is additive and does not replace the 6.5.4 hard-constraint layer.

## 6.6.3 Dynamic Routing Signals

Adds `RoutingRuntimeSignalService`, an in-memory runtime signal store. Provider/model latency is updated with an exponentially weighted observation and availability is derived from success/failure observations with configured baselines. Signals are advisory scoring inputs, not security or availability hard constraints.

`GatewayServiceImpl` records provider invocation success/failure and latency so later routing decisions can use runtime observations.

## 6.6.4 Adaptive Scoring

Adds `AdaptiveRoutingScoringService`. It deterministically adapts the existing 6.5 scoring weights for cost, latency, availability, and policy preference based on routing priority and Unity mode. No LLM or machine-learning decision-maker is introduced.

## 6.6.5 Routing Explainability

Adds `RoutingDecisionExplanation` to `RoutingDecisionMetadata`. Explanations identify the selection reason, applied signals, and capability requirements while preserving the existing provider/model decision contract.

## 6.6.6 Decision Analytics Integration

Extends `RoutingAnalytics` with intelligent-decision count, Unity-decision count, and routing-priority counts. Existing analytics behavior remains backward compatible.

## 6.6.7 Unity Integration

Unity is exposed through `ChatRequest.extensiveResearch`. It is disabled by default through `gateway.routing.unity.enabled=false` and can only be activated when the platform gate is enabled. The tenant must also have `Feature.EXTENSIVE_RESEARCH`; the feature is provisioned by default only for the ENTERPRISE plan. When enabled, Unity reuses the normal routing pipeline and cannot bypass hard constraints.

## 6.6.8 Tests & Runtime Validation

Adds unit coverage for adaptive scoring, capability matching, runtime signals, and the decision-intelligence context. Existing 6.5 tests remain the regression boundary. Full Maven verification must be performed in the project's Maven/IntelliJ environment because Maven is not installed in the implementation container.
