# AegisAI Logging Foundation

## Purpose

The logging foundation provides persistent, correlation-aware application and performance logs for local development and future centralized observability.

## Log files

When the application starts, logs are written under `logs/` by default:

- `logs/aegisai.log` — application logs
- `logs/aegisai-performance.log` — request/provider/stage performance events
- `logs/archive/` — compressed rotated files

Override the directory with `LOG_DIR`.

Log files are size/time rotated and retained for 30 days with a 1 GB total cap per appender.

## Correlation

Every HTTP request receives an `X-Request-ID` UUID. The same value is:

- placed in SLF4J MDC as `requestId`
- attached to the servlet request
- returned as the `X-Request-ID` response header
- reused by `/api/chat` as its application request ID

Authenticated requests additionally populate `tenantId` and `tenantCode` in MDC.

## Performance events

`aegisai-performance.log` records metadata-only events for:

- request start/completion
- authentication
- entitlement
- firewall/policy evaluation
- PII masking/token-vault processing
- routing
- provider entitlement
- provider execution
- usage/cost persistence
- response restoration
- audit/metrics
- individual provider attempts
- provider failover attempts

Provider events record provider, model, attempt, duration and outcome. Prompts, responses, API keys and provider credentials are intentionally excluded.

## Latency diagnosis

For `/api/chat`, compare:

`REQUEST_COMPLETED.totalLatencyMs`

against:

`STAGE_COMPLETED stage=PROVIDER_EXECUTION durationMs=...`

and the provider-specific `PROVIDER_REQUEST_COMPLETED` event.

This distinguishes provider wait time from gateway processing time and makes retry/failover overhead visible.
