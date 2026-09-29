# AIRouter Configuration Governance

## Decision

AIRouter will use a hybrid configuration model:

- YAML/environment/secrets for deployment, infrastructure, bootstrap and security-boundary settings.
- Database + API + UI for runtime/business/customer configuration.
- Platform-admin database configuration for operator-controlled commercial/catalog values that should change without redeployment.
- Database configuration never replaces deployment secrets or infrastructure ceilings.

The rule is simple:

> If changing a value changes how a customer, project, environment or application behaves, it belongs in the database and should be exposed through the appropriate UI/API.

> If changing a value changes how the AIRouter deployment itself starts, connects to infrastructure, protects secrets, or establishes a hard safety ceiling, it remains deployment configuration.

## Configuration ownership matrix

| Area | Source of truth | UI/API? | Decision |
|---|---|---:|---|
| Database URL/credentials | Environment/secret manager | No | Deployment-only |
| Encryption master key | Environment/secret manager | No | Deployment-only |
| SMTP credentials | Environment/secret manager | No | Deployment-only |
| Twilio credentials | Environment/secret manager | No | Deployment-only |
| Payment webhook secret | Environment/secret manager | No | Deployment-only |
| Server port | Environment/YAML | No | Deployment-only |
| Spring/JPA settings | Environment/YAML | No | Deployment-only |
| Logging levels | Environment/YAML | Ops only | Deployment/ops |
| Firewall service URL | Environment/YAML | No | Infrastructure |
| Firewall connect/read ceiling | Environment/YAML | No | Infrastructure safety boundary |
| Firewall fail-open | Environment/YAML | No | Security boundary |
| Maximum request/file byte ceiling | Environment/YAML | No | Hard deployment safety ceiling |
| Provider base URLs | Environment/YAML initially | Platform admin later | Infrastructure |
| Customer provider credentials | Encrypted DB | Yes | Customer/provider UI |
| Provider enablement for a project | DB | Yes | Business/Project UI |
| Model catalog | DB | Yes for platform admins | Platform control plane |
| Model availability per project | DB | Yes | Business UI |
| Model capabilities | DB | Yes for platform admins | Control plane |
| Model context windows | DB | Yes for platform admins | Control plane |
| Model pricing | DB | Yes for platform admins | Commercial control plane |
| Personal feature entitlements | DB | Yes for platform/admin policy | Move out of static list |
| Personal free-model list | DB | Yes for platform/admin policy | Commercial policy |
| Personal quotas | DB | Yes | Plan/account/project policy |
| Personal rate limits | DB | Yes | Plan/account/project policy |
| Personal concurrency limit | DB | Yes | Plan/account/project policy |
| Business rate limits | DB | Yes | Project/environment/API-key policy |
| Budgets | DB | Yes | Customer policy |
| Spend alerts | DB | Yes | Customer policy |
| Routing weights | DB | Yes | Routing policy |
| Routing provider/model preferences | DB | Yes | Routing policy |
| Routing fallback chain | DB | Yes | Routing policy |
| Routing health algorithm constants | YAML initially | Admin later if needed | Platform algorithm |
| Provider HTTP timeout ceiling | YAML | No | Infrastructure safety |
| Provider concurrency capacity | YAML initially | Admin later | Platform capacity |
| Exact cache enabled | DB policy + YAML hard ceiling | Yes | Runtime policy |
| Cache TTL | DB policy + YAML hard ceiling | Yes | Runtime policy |
| RAG enabled | DB | Yes | Product/plan policy |
| RAG top-k/retrieval strategy | DB | Yes | Project/KB policy |
| RAG chunking defaults | DB | Yes for admin; project overrides later | Control plane |
| RAG max file size | YAML hard ceiling + DB lower customer limit | Yes for customer limit | Safety + policy |
| Auth session TTL | YAML initially | No | Security/platform policy |
| Email verification TTL | YAML initially | No | Security/platform policy |
| Phone verification TTL | YAML initially | No | Security/platform policy |
| Auto verification | YAML | No | Development/security control |
| Expose verification token/code | YAML | No | Never customer-configurable |
| Payment currency | DB/platform account policy | Admin | Commercial |
| Credit packages | DB | Admin | Commercial |
| Subscription plans | DB | Admin | Commercial |
| Business seat pricing | DB | Admin | Commercial |
| Routed-spend fee | DB | Admin | Commercial |
| Cache-savings share | DB | Admin | Commercial |
| API key scopes | DB policy/catalog | Yes within allowed policy | Security policy |
| Audit retention | DB customer policy with platform maximum | Yes | Governance |
| Data retention hard maximum | YAML | No | Compliance/safety |
| Feature flags | DB | Admin | Runtime rollout |
| Failure injection | YAML | No | Test-only safety |

## Three-level configuration hierarchy

Runtime configuration should eventually resolve in this order:

1. Platform hard ceiling / deployment guardrail
2. Platform defaults
3. Plan or organization policy
4. Project policy
5. Environment/API-key policy
6. Request-level limits where explicitly supported

A lower layer can tighten a value but must not exceed a platform safety ceiling.

Example:

    Platform max output tokens = 32768
    Organization max = 8192
    Project max = 4096
    Request max = 2048
    Effective max = 2048

If an organization attempts to configure 65536, the API must reject it rather than silently exceeding the platform ceiling.

## What should NOT move to the database

Do not put these in customer-editable DB configuration:

- database credentials
- encryption master key
- application secrets
- provider infrastructure credentials owned by AIRouter
- SMTP/Twilio secrets
- payment webhook signing secrets
- firewall internal credentials
- deployment endpoints
- JVM/server settings
- hard network/security boundaries
- maximum resource ceilings intended to protect the deployment
- development-only verification bypasses
- failure-injection controls

These belong in environment variables, a secret manager, deployment configuration or infrastructure configuration.

## What SHOULD move to the database

The following are runtime product configuration and should not require a deployment.

### Customer policy

- quotas
- rate limits
- concurrency
- budgets
- spend alerts
- model allowlists
- provider allowlists
- routing policies
- fallback chains
- RAG settings
- security policies
- API-key scopes
- project/environment settings

### Platform catalog

- providers
- models
- capabilities
- context windows
- pricing
- model availability
- feature entitlements

### Commercial configuration

- plans
- plan limits
- seat pricing
- credit packages
- routed-spend fee
- future verified-savings share

## Configuration API design

Do not expose arbitrary key/value configuration to customers.

Use typed domain APIs:

    /api/organizations/{id}/settings
    /api/projects/{id}/settings
    /api/projects/{id}/providers
    /api/projects/{id}/models
    /api/projects/{id}/routing-policies
    /api/projects/{id}/budgets
    /api/projects/{id}/security-policies
    /api/projects/{id}/api-keys

The API validates ownership, role, plan entitlement and platform ceilings.

## Auditability

Every configuration mutation must record:

- actor
- organization/account
- project/environment
- setting/domain
- old value
- new value
- timestamp
- source (UI/API/admin)
- request ID

Secrets must never be written to audit logs.

## Migration strategy

Do not migrate every YAML value into the database at once.

### Migration 1 — Control-plane foundation

Create typed DB entities/tables for:

- plans
- feature entitlements
- model catalog
- provider catalog
- project/provider configuration
- quotas
- rate limits
- routing policies
- budgets

### Migration 2 — Runtime resolution

Introduce a configuration resolver:

    Deployment Guardrails
            ↓
    Platform Defaults
            ↓
    Plan Policy
            ↓
    Organization Policy
            ↓
    Project Policy
            ↓
    Environment/API-Key Policy
            ↓
    Effective Runtime Configuration

### Migration 3 — UI/API

Expose only the settings appropriate to the current actor and scope.

### Migration 4 — Remove duplicated YAML

Only after DB resolution is tested and observable should the corresponding YAML values be retired.

## Current AIRouter Personal recommendation

For the current Personal v1 branch, do not perform a large configuration rewrite yet.

First establish the configuration ownership model and shared resolver interfaces. Then migrate the highest-value runtime policies:

1. Personal quotas
2. Personal rate/concurrency limits
3. feature entitlements
4. model catalog/availability
5. pricing
6. free-model policy
7. routing policies

Keep infrastructure/security secrets and hard ceilings in YAML/environment configuration.

This prevents a risky everything-into-DB migration while giving the Business Suite a clean configuration control plane.
