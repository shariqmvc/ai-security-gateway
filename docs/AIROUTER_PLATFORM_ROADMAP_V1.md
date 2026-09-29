# AIRouter Platform Roadmap v1

## Product definition

AIRouter is an AI infrastructure and middleware platform between applications/SaaS products and AI model providers. It provides a unified API and control plane for model access, routing, security, governance, observability, optimization, and AI spend.

Personal is the first reference client and dogfooding surface; it is not the architectural boundary of AIRouter.

## Product surfaces

- AIRouter Personal — individual developer workspace, BYOK, chat/RAG, usage and credits.
- AIRouter Developer Platform — API, documentation, playground, SDKs and integration lifecycle.
- AIRouter Business Suite — organizations, projects, environments, providers, routing, governance, usage, costs, security, team and billing.
- AIRouter Gateway Core — provider abstraction, OpenAI-compatible API, routing, failover, streaming, metering and execution controls.
- AIRouter Enterprise — SSO/SCIM, advanced governance, compliance, private networking and enterprise operations.

## Phase 0 — Personal v1 freeze

Status: substantially complete.

Keep Personal focused on bug fixes, security, correctness and UX polish. Avoid expanding Personal into the full Business product.

## Phase 1 — Gateway Foundation

1. Stable OpenAI-compatible API contracts.
2. Provider adapter abstraction.
3. Streaming normalization.
4. Request IDs and idempotency.
5. Normalized provider errors.
6. Retry and timeout policy.
7. Provider failover.
8. Rate limiting and concurrency controls.
9. Usage/token metering.
10. Cost calculation.
11. Gateway-level authentication and API-key authorization.

## Phase 2 — Developer Platform

1. Developer portal.
2. API reference.
3. Authentication and quick-start documentation.
4. API playground.
5. Model catalog.
6. TypeScript SDK.
7. Python SDK.
8. Java SDK.
9. Webhook/event documentation.

SDKs follow stabilization of the public API contract.

## Phase 3 — Organizations, Projects and Environments

Introduce Organization, Membership, Role, Project, Environment, API Key, Provider Connection, Model Configuration, Routing Policy, Budget, Usage Record and Audit Event.

Separate development, staging and production configuration.

## Phase 4 — Business Suite

Business UI navigation:

- Overview
- Playground
- Projects
- API Keys
- Providers
- Models
- Routing
- Policies
- Security
- Usage
- Costs
- Budgets
- Logs
- Analytics
- Team
- Audit
- Billing
- Settings

The Business Suite is a distinct product surface, not a larger Personal dashboard.

## Phase 5 — Provider and Model Control Plane

- Provider connections
- Credential validation
- Provider health
- Provider enable/disable
- Model catalog
- Model capabilities
- Context windows
- pricing metadata
- organization/project model availability

Provider credentials supplied by customers are encrypted and stored in the database. Platform infrastructure secrets remain deployment-managed.

## Phase 6 — Intelligent Routing

Support routing policies using capability, price, latency, availability, reliability, provider health, policy preference, budget and fallback rules.

Applications may request a concrete model or an AIRouter policy such as auto.

## Phase 7 — Security and AI Firewall

- PII detection
- prompt injection detection
- jailbreak detection
- DLP
- input/output security policies
- model/provider restrictions
- audit of security decisions

## Phase 8 — Governance and FinOps

- budgets
- quotas
- spend alerts
- token limits
- request limits
- model restrictions
- provider restrictions
- cost attribution
- organization/project/environment/API-key attribution

## Phase 9 — RAG and Context Infrastructure

- knowledge bases
- ingestion
- embeddings
- retrieval
- hybrid search
- provenance
- citations
- context assembly
- context optimization

## Phase 10 — Caching and AI Cost Optimization

- exact cache
- semantic cache
- embedding cache
- context reuse
- Headroom/context compression
- verified savings measurement

Future savings-share monetization must use measured, auditable savings.

## Phase 11 — Advanced Execution and Resilience

- parallel execution
- fallback chains
- hedged requests
- circuit breakers
- provider health
- advanced retry strategies

## Phase 12 — Multimodal and Streaming Infrastructure

Normalize text, image, document, audio and future multimodal provider capabilities behind the AIRouter API.

## Phase 13 — LLMOps and Evaluation

- request traces
- provider/model latency
- token usage
- cost
- retries
- routing decisions
- cache savings
- evaluation
- regression testing
- production feedback

## Phase 14 — Agents, Tools and MCP

Introduce shared agent infrastructure, tools, MCP, memory and secured tool execution after the gateway/control-plane foundation is stable.

## Phase 15 — Enterprise

- SSO/SAML
- SCIM
- advanced RBAC
- enterprise audit
- compliance controls
- data residency
- retention policies
- private networking
- dedicated infrastructure
- enterprise billing/support

## Commercial alignment

- Free: BYOK acquisition.
- Pro: starting hypothesis of $12/month.
- Business: starting hypothesis of $30/seat/month with a 5-seat minimum plus 4% on eligible routed spend.
- Enterprise: custom.
- AIRouter credits: secondary payment/ecosystem mechanism.
- Future cache-savings share: approximately 10% of verified savings as a later hypothesis.

Pricing is a commercial policy and must not be hard-coded into request execution logic.

## Architecture direction

Personal, Business and Developer surfaces consume shared AIRouter Gateway/Core capabilities. Personal-specific code must not become the platform boundary.

    Applications / SaaS
            |
            v
    Developer API / SDKs
            |
            v
    AIRouter Gateway Core
     |       |       |       |
    Auth   Routing  Security  Metering
     |       |       |       |
     +-------+-------+-------+
             |
             v
        AI Providers

This document is the canonical roadmap baseline for the platform transition.
