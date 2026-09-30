# Business Suite Boundary and Platform Architecture

## Decision

AIRouter remains the AI middleware/platform layer. The future Business Suite should be a separate application codebase that consumes AIRouter through stable APIs.

Business-domain functionality such as company registration, departments, employees, HR workflows, payroll, approvals, and organization records must not be placed in the gateway.

## AIRouter owns

- gateway authentication and API keys
- provider, model, and endpoint abstraction
- routing and routing policy
- health scoring
- circuit breakers and failover
- streaming and OpenAI-compatible API contracts
- inference accounting, credits, and usage
- AI security and policy enforcement
- RAG and inference execution primitives
- provider credentials and secret isolation
- gateway observability and operational telemetry

## Business Suite owns

- tenant and company registration
- organization and legal/business profiles
- users and employees
- departments and reporting structures
- roles and permissions
- invitations and onboarding
- business workflows and approvals
- HR and business records
- organization-specific AI assistants and policies
- business documents and domain data
- business billing plans and entitlements

## Dependency direction

    Business Suite
          |
          | HTTPS / SDK
          v
    AIRouter Middleware
          |
          +--> Provider adapters
          +--> Routing / health / failover
          +--> AI security
          +--> Usage / credits
          |
          +--> AI providers

The Business Suite database must not become a foreign-key extension of the AIRouter database.

## Tenant propagation

Business Suite sends a stable gateway customer/tenant identifier and request-scoped metadata to AIRouter. AIRouter may use that identity for authorization, quotas, billing attribution, policy selection, and observability.

AIRouter should not persist Business Suite employee or department records.

## Shared code

Prefer small versioned SDKs and API contracts over sharing internal Java domain classes between applications.

Initial SDK targets:
- Java
- TypeScript
- optional Python

Authentication, request DTOs, routing metadata, and error contracts belong in the SDK/API contract, not in a shared business-domain JAR.

## Why separate codebases

This preserves bounded contexts and lets AIRouter serve multiple products. Business Suite can evolve independently, while AI middleware can scale and deploy independently. Business data also remains isolated from AI infrastructure data.

## Migration rule

Do not create Business Suite tables inside the AI Gateway schema.

When Business Suite work begins, create a new repository/application and integrate against the frozen AIRouter API contract. Start with tenant/company registration and identity, then employee/department management. AI features consume AIRouter rather than reimplementing provider integrations.
