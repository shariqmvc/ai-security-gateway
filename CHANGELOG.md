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

### Test and architecture hardening
- Failover fallback default models are now resolved through the model registry rather than instantiating provider adapters.
- Policy routing validates provider availability before endpoint/model resolution.
- Updated isolated routing tests to stub the endpoint-aware provider/model registry boundary.
- Updated gateway streaming tests to exercise the StreamingProviderFailoverService boundary and the explicit status/routing SSE events.
- Restored /api/chat authorization delegation to the mandatory X-API-Key AuthenticationFilter; Spring role authorization does not duplicate that boundary.

- Removed remaining Core provider imports of the Personal credential resolver; Gemini, Groq, Mistral, XAI, and native OpenAI-compatible providers now depend on the Core credential-resolution contract.
- Configured fallback providers now resolve their default model only when the fallback is actually attempted.
- Preserved registry exceptions during policy candidate resolution while retaining compatibility with isolated single-model registry tests.
- Chat remains authenticated through the mandatory API-key authentication filter while Spring Security performs only the final authenticated check.
- Personal verification delivery is optional for isolated service construction; production delivery remains invoked when configured.

- Removed the duplicate circuit-breaker regression test left by the endpoint migration.
- Updated Ollama provider tests for endpoint-registry injection and corrected Mockito matcher usage.
- Preserved backward-compatible provider/model circuit-breaker calls when no endpoint is specified.
- Deferred configured fallback default-model resolution until failover is actually required.
- Preserved routing compatibility for registry adapters that expose a single model definition.
- Isolated provider credential lookup behind a core `ProviderCredentialResolver` contract so Core providers no longer import Personal product packages.
- Kept attached-document materialization optional for legacy unit-test construction paths.
- Removed duplicate fallback-success attempt telemetry.
- Aligned Gemini native-document regression expectations with the native-document contract.

### Verification
- Added regression coverage for endpoint-isolated circuit state.
- Added regression coverage for half-open circuit probing.
- Next verification gate: mvn clean test, application startup, and end-to-end routing/failover tests.

## 2026-09
- Added endpoint-aware provider routing, health scoring, failover, and streaming telemetry.
- Added endpoint-aware routing metadata to the OpenAI-compatible SSE contract.
