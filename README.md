# AI Security Gateway

AIRouter middleware for secure, policy-driven access to multiple AI providers.

## Current architecture

    Client / SDK
         |
         v
    OpenAI-compatible API
         |
         v
    Authentication / Policy
         |
         v
    Routing: provider + model + endpoint
         |
         +--> Health scoring
         +--> Circuit breaker
         +--> Failover
         +--> Usage / billing
         |
         v
    Provider adapters
    OpenAI / Anthropic / Gemini / Groq / Mistral / Ollama / XAI

### Routing identity

A routing candidate is identified by provider + model + endpoint. This identity is used consistently for endpoint-aware routing, health state, circuit-breaker state, failover metadata, and routing telemetry.

### Streaming contract

Normal streaming deltas remain unchanged. The gateway emits a dedicated routing event before DONE. The routing event contains the selected provider/model/endpoint, requested model/provider, failover provenance, and provider-attempt history.

## Database migrations

- V38: routing-health endpoint identity
- V39: routing-outcome endpoint identity

## Development verification

    mvn clean test

Then start the application and exercise the OpenAI-compatible chat endpoint with normal and streaming requests.

## Product boundary

AIRouter is the AI infrastructure layer. Business-domain functionality belongs in a separate Business Suite application.

The Business Suite owns tenants/companies, employees, departments, roles, business workflows, and business data. It consumes AIRouter through a stable API/SDK.

See:
- CHANGELOG.md
- docs/architecture/BUSINESS-SUITE-BOUNDARY.md
