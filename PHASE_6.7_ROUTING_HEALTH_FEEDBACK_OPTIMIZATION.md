# AegisAI 6.7 — Routing Health, Feedback & Optimization

## Scope

6.7 closes the intelligent-routing feedback loop introduced in 6.5 and 6.6.

### 6.7.1 Routing Outcome Capture
Every provider invocation that reaches execution records a durable routing outcome containing request, tenant, provider/model, routing strategy, selected score/rank, candidate count, routing priority, Unity/extensive-research state, execution role, latency, success/failure and failure category.

### 6.7.2 Persistent Provider/Model Health
`ROUTING_HEALTH_PROFILE` persists provider/model health across application restarts. It maintains success/failure counts, consecutive failures, EWMA latency, P95 latency, availability, health status and observation timestamps.

### 6.7.3 Health & Reliability Metrics
Health status is deterministic:
- UNKNOWN until the configured minimum observation count is reached.
- HEALTHY when availability is at/above the degraded threshold.
- DEGRADED when availability is below the degraded threshold but above the unhealthy threshold.
- UNHEALTHY when availability is below the unhealthy threshold or the consecutive-failure threshold is reached.

Signal freshness is governed by `signal-ttl-seconds`.

### 6.7.4 Failure-Aware Routing
Fresh UNHEALTHY candidates are removed before scoring. UNKNOWN and stale signals do not silently become hard routing exclusions.

### 6.7.5 Dynamic Runtime Signal Management
The runtime signal facade uses persistent health in production and retains its lightweight in-memory constructor for isolated tests. Fresh EWMA latency and availability feed the existing scoring engine.

### 6.7.6 Routing Optimization
`RoutingOptimizationService` deterministically adjusts soft scoring weights from fresh runtime signals and routing priority. It cannot introduce candidates, override policy, bypass hard constraints, or select a provider itself.

### 6.7.7 Optimization Policy & Governance
The effective order remains:
1. authentication/entitlement
2. policy
3. eligibility
4. hard constraints
5. capability matching
6. failure-aware health gate
7. adaptive/optimized scoring
8. deterministic selection

Unity remains an optional routing context and never bypasses governance.

### 6.7.8 Tests & Runtime Validation
Added unit coverage for:
- health state transitions
- consecutive failures
- stale signal behavior
- failure-aware candidate filtering
- optimization weight adaptation
- durable routing outcome capture

A runtime endpoint is available at:
- `GET /api/routing/health`
- `GET /api/routing/health/{provider}/{model}`

## Configuration

```yaml
gateway:
  routing:
    health:
      enabled: true
      reject-unhealthy: true
      min-observations: 3
      consecutive-failure-threshold: 3
      degraded-availability: 0.90
      unhealthy-availability: 0.70
      ewma-alpha: 0.20
      signal-ttl-seconds: 300
```

## Database

Flyway migration:
`V2__routing_health_and_outcomes.sql`

Tables:
- `ROUTING_OUTCOME`
- `ROUTING_HEALTH_PROFILE`

## Security note

Provider credentials and encryption-key fallbacks should be supplied only through environment/secret management before production deployment. The current source configuration contains fallback secret material and should be rotated/removed.
