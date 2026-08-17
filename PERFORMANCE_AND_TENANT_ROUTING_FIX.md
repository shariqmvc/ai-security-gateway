# Performance and Tenant Schema Routing Fix

## Findings from latest performance log

The latest `logs/aegisai-performance.log` contains three completed `/api/chat` samples:

| Request | Total | Provider | Gateway overhead | Outcome |
|---|---:|---:|---:|---|
| `2996408d-b2f9-404a-af22-412f0600619f` | 6002 ms | 5981 ms | 21 ms | FAILED |
| `9280b831-89b5-4d70-995f-9a30db784bd1` | 32844 ms | 32824 ms | 20 ms | FAILED |
| `46f84540-56ac-4231-9988-69bdf389968c` | 6267 ms | 6254 ms | **13 ms** | FAILED |

Current gateway-overhead statistics from the supplied log:

- min: 13 ms
- p50: 20 ms
- p95: 21 ms
- p99: 21 ms
- max: 21 ms
- average: 18 ms

The gateway is therefore already below the 30–40 ms target. The high end-to-end latency is provider/failover time, not gateway processing time.

## Functional issue found

The latest requests fail after provider execution because tenant operational JPA writes intermittently execute against the `public` schema. PostgreSQL correctly rejects those writes through the Phase 7 tenant-isolation trigger:

`Tenant operational table tenant_quota_usage is not writable in public schema; establish tenant schema routing first`

The same problem also appears for `request_audit` during asynchronous failure persistence.

## Root cause

`TenantSchemaRoutingService` used `TransactionSynchronizationManager` as a per-transaction optimization marker, but the marker was bound without registering a transaction-completion callback to remove it.

Because async post-provider work runs on a pooled executor, the marker could remain attached to a worker thread after its transaction completed. A later transaction for the same tenant could see the stale marker and skip `SET LOCAL search_path`, leaving PostgreSQL on `public` even though the `TenantSchemaContext` was correct.

## Fix

The applied-schema marker is now explicitly transaction-scoped:

1. stale markers are defensively removed;
2. `SET LOCAL search_path` is applied for the active transaction;
3. the marker is bound only for the current transaction;
4. a `TransactionSynchronization.afterCompletion` callback removes the marker;
5. a regression test verifies that the same tenant causes `SET LOCAL search_path` to execute again in a subsequent transaction.

This preserves the fast-path optimization within a transaction while preventing cross-request/thread leakage.

## Provider-side observations

One recent Gemini request received HTTP 429 because the supplied Gemini free-tier project exceeded its request quota. The configured Ollama fallback then spent approximately 30 seconds before failing with `ResourceAccessException`. That explains the 32.8-second request and is provider/failover latency, not gateway overhead.

No provider timeout was changed in this fix because doing so would change failover/reliability policy rather than the gateway's own processing latency.

## Verification limitation

The uploaded project does not contain a Maven wrapper, and the execution environment has Java 21 but no Maven installation. Source-level checks and the regression-test code were added, but Maven compilation/test execution must be run in the developer environment:

```text
mvn -Dtest=TenantOperationalContextSafetyTest test
mvn clean test
```
