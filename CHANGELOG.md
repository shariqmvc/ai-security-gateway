# Changelog

All notable changes to the AI Security Gateway are documented here.

## 2026-10-01 — Endpoint-aware middleware completion pass

### Routing and failover
- Completed the provider/model/endpoint routing identity migration.
- Circuit-breaker state is isolated by provider, model, and endpoint.
- Routing-health persistence resolves profiles by provider, model, and endpoint.
- Streaming routing SSE exposes selected endpoint and failover provenance.
- OpenAI-compatible streaming exposes a dedicated chat.completion.routing event before DONE.

### Database
- Fixed V38 routing-health migration to drop the PostgreSQL uniqueness constraint rather than its backing index.
- V38 replaces provider/model uniqueness with provider/model/endpoint uniqueness.
- V39 persists endpoint identity for routing outcomes.

### Verification
- Added regression coverage for endpoint-isolated circuit state.
- Added regression coverage for half-open circuit probing.
- Next verification gate: mvn clean test, application startup, and end-to-end routing/failover tests.

## 2026-09
- Added endpoint-aware provider routing, health scoring, failover, and streaming telemetry.
- Added endpoint-aware routing metadata to the OpenAI-compatible SSE contract.
