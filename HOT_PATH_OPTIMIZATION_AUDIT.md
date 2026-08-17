# AegisAI Hot-Path Algorithm & Data-Structure Optimization Audit

## Status

Implemented optimization pass — August 2026.

This audit reviews the uploaded `phase-optimization` codebase with emphasis on the `/api/chat` synchronous critical path. The objective is to reduce CPU, allocation, synchronization and database overhead without weakening authentication, tenant isolation, firewall, policy, routing, cost or audit controls.

## Executive Summary

The codebase had several concrete optimization opportunities beyond the already-achieved gateway-latency reduction:

1. **Routing registry recreation** — provider/model definitions were recreated on every lookup and provider factory calls were repeated.
2. **Routing health database reads** — failure-aware routing could perform a repository lookup for every candidate during every request.
3. **Candidate scoring normalization** — normalization scanned the complete candidate value list for every candidate and strategy, producing an avoidable `O(S*C^2)` section.
4. **Candidate selection** — production selection can rank candidates once and the routing layer currently asks for ranking again for metadata.
5. **Firewall regex compilation** — `Pattern.compile()` was executed inside the request path for every firewall rule evaluation.
6. **PII masking allocations** — a `StringBuffer` was allocated for each PII pattern even when no match existed; token formatting used `String.format()` for each generated token.

The implemented pass addresses items 1–6. Candidate selection now exposes a selection-result contract so the deterministic ranking is produced once and reused for decision metadata.

## 1. Optimization Principle

The AegisAI hot path should follow:

> Compute once, index once, cache stable state, evaluate dynamic state, allocate minimally, and never perform unnecessary synchronous work.

Security and governance remain on the critical path. The goal is to make those controls inexpensive, not to remove them.

## 2. Provider/Model Registry

### Before

`ProviderRegistryImpl.find()` called the provider factory and created a new `ProviderDefinition` for each lookup.

`ModelRegistryImpl.find()` called the provider factory, read the default model, and created a new `ModelDefinition` for each lookup.

This was unnecessary for request-time routing because the provider implementations and their default models are startup/control-plane state in the current MVP.

### After

The registry is built once at application startup using:

- `EnumMap<Provider, ProviderDefinition>` for provider lookup;
- `Map<Provider, ModelDefinition>` for provider/model lookup;
- `Map<String, ModelDefinition>` for model-id lookup;
- immutable lists/maps exposed to readers.

### Complexity

Provider/model lookup changes from repeated provider-factory work plus object construction to approximately:

- provider lookup: `O(1)`
- provider/model lookup: `O(1)`
- model-id lookup: `O(1)`

This also reduces allocation and virtual-call pressure on the routing path.

## 3. Routing Health

### Before

`FailureAwareCandidateFilter` called `RoutingHealthService.isHealthyForRouting()` for every candidate. The health service then queried `ROUTING_HEALTH_PROFILE` for each candidate.

With `C` candidates this could become approximately:

`O(C)` database lookups per routing decision.

### After

A read-mostly `ConcurrentHashMap<RoutingCandidate, RoutingHealthSnapshot>` caches routing-health snapshots.

- First access may consult the durable repository.
- Subsequent request-path checks use memory.
- Health updates refresh the cache.
- Snapshot freshness is recomputed from `lastObservedAt` and the configured TTL.
- The database remains the durable source of truth.

### Result

Normal routing health checks are now memory lookups rather than one database query per candidate.

## 4. Candidate Scoring

### Before

The scoring engine first calculated raw values for every strategy and candidate. During normalization it then scanned the entire raw-value list again for every candidate.

For `S` scoring strategies and `C` candidates, that produced an avoidable `O(S*C^2)` normalization component.

### After

For each strategy the engine computes:

- all raw values once;
- minimum once;
- maximum once.

Normalization then uses the stored min/max directly.

Complexity becomes approximately:

`O(S*C)`

instead of:

`O(S*C^2)`

The output contract remains unchanged: every candidate still receives the configured score components and deterministic total score.

## 5. Firewall Regex Compilation

### Before

`RuleEvaluator` called `Pattern.compile(rule.getRegex())` for every request and every firewall rule.

Regex compilation is substantially more expensive than reusing an already compiled `Pattern`.

### After

A concurrent pattern cache stores compiled patterns by their regex string:

`ConcurrentMap<String, Pattern>`

The first use compiles the expression. Subsequent requests reuse the compiled pattern.

This is especially important because firewall evaluation sits directly on the `/api/chat` critical path.

## 6. PII Masking

### Before

The masking implementation allocated a `StringBuffer` for each of the email, phone and credit-card passes even when no match was found.

Token generation also used `String.format()` for each byte of a generated token.

### After

The masker first performs a `Matcher.find()` and returns the original string immediately when no match exists. A replacement buffer is created only when a match is present.

Token generation now uses a fixed hexadecimal character table and a pre-sized `StringBuilder`, avoiding per-byte `String.format()` calls.

The security semantics and token format remain unchanged.

## 7. Candidate Selection — Single-Rank Selection

The production route now calls `selectWithRanking()` and receives `CandidateSelectionResult { selected, rankedCandidates }`. The ranking is produced once and reused for decision metadata. This removes the previous second `O(C log C)` sort while preserving the selection abstraction.

## 8. Additional Findings Not Yet Changed

### RoutingPolicy membership

`RoutingPolicy.allowedProviders` and `allowedModels` currently use `List.contains()`, which is `O(n)`. If these lists become large, immutable `Set`-backed membership would provide approximately `O(1)` lookup. For the current small policy lists this is not expected to be a material latency contributor, so it remains a lower-priority change.

### Routing strategy selection

`RoutingService` scans the ordered strategy list using a stream. The strategy count is small and bounded, so this is not currently a meaningful bottleneck.

### Candidate streams

Several candidate filters use streams and `distinct()`. These are readable and candidate counts are expected to remain small. They should only be replaced with imperative loops after profiling demonstrates measurable CPU/allocation impact.

### Tenant schema routing

The existing transaction-scoped schema marker is retained. This optimization must not be replaced with unsafe thread-local state because pooled executor threads can leak state across requests.

## 9. Security Invariants Preserved

This optimization pass does not bypass:

- authentication;
- tenant authorization;
- tenant schema validation;
- firewall inspection;
- policy evaluation;
- provider entitlement;
- quota/budget guardrails;
- provider health gating;
- audit/usage/cost persistence semantics.

## 10. Verification

The uploaded project contains focused tests for routing, scoring, provider/model registry, PII detection and tenant isolation.

The environment used for this audit does not contain Maven and the repository has no Maven wrapper, so a full Maven build cannot be truthfully reported as executed here.

Developer-side verification should include:

```text
mvn clean test
```

and specifically:

```text
mvn -Dtest=CandidateScoringEngineImplTest test
mvn -Dtest=ModelRegistryTest test
mvn -Dtest=ProviderRegistryTest test
mvn -Dtest=PIIDetectionServiceTest test
mvn -Dtest=RoutingPipelineRuntimeValidationTest test
```

Then run representative `/api/chat` benchmarks and compare:

- gatewayOverheadMs;
- p50/p95/p99;
- CPU usage;
- allocation/GC behavior;
- database query count;
- routing decision time.

## 11. Target Outcome

The objective is not merely a lower single-request number. The target is a lower and more stable distribution:

- gateway fast path: approximately `5–10 ms` where environment and workload permit;
- p50: `<= 10 ms`;
- p95: `<= 20 ms`;
- investigate sustained p95 above `30 ms`.

The provider remains separately measured and should not be conflated with gateway overhead.

## 12. Recommended Next Step

Run the focused test suite and profile the complete `/api/chat` hot path under warm-cache load. The next measurements should use a representative request set rather than a single Postman request. After that, inspect database query counts and allocation/GC behavior to identify the next measurable bottleneck.
