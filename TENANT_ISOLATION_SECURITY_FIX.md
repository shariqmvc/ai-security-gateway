# Tenant Isolation Security Fix

## Current hardening scope

This update addresses the cross-tenant cost-data authorization vulnerability discovered during Phase 7.4 Postman validation and fixes the follow-up `CostServiceTest` regression reported by the full Maven test run.

### Security boundary

For tenant-scoped API requests:

```text
X-API-Key
    -> authenticated tenant
    -> TenantContext
    -> TenantSchemaContext
    -> tenant physical schema
```

A client-controlled tenant ID is never allowed to select another tenant's schema.

### Cost API behavior

| API key | Requested tenant | Expected |
|---|---|---|
| Tenant A | Tenant A | `200 OK` + Tenant A data |
| Tenant B | Tenant B | `200 OK` + Tenant B data |
| Tenant A | Tenant B | `403 Forbidden` |
| Tenant B | Tenant A | `403 Forbidden` |

`/api/cost/summary`, `/provider/{provider}`, `/model/{model}`, `/today`, and `/month` are routed using the authenticated tenant schema.

`/api/cost/tenant/{tenantId}` first performs `TenantAccessGuard.requireAccess(tenantId)`. Because that guard requires the requested ID to equal the authenticated tenant ID, the authorized path variable is then safe to use for the repository query.

### Follow-up test fix

`CostServiceImpl.getTenantSummary()` previously called `requireAuthenticatedTenant()` after `requireAccess()`. In the Mockito unit test this returned `null` because the guard was mocked, causing a repository call with a null tenant ID.

The service now uses the already-authorized `tenantId` argument. This preserves the security invariant while avoiding a redundant independent ThreadLocal lookup.

`CostServiceTest` was also changed so save-specific Mockito stubs are local to the save tests. This removes strict Mockito `UnnecessaryStubbingException` errors from authorization-only tests.

### Schema routing defense in depth

`TenantSchemaRoutingService`:

- rejects explicit tenant routing when an authenticated `TenantContext` exists for a different tenant;
- validates persisted `schema_name` against the deterministic `tenant_<uuid-without-hyphens>` schema name;
- validates the authenticated schema context against the authenticated tenant identity;
- rejects invalid schema identifiers.

### Operational tenant data

Tenant operational services route before accessing:

- `REQUEST_AUDIT`
- `TOKEN_VAULT`
- `TOKEN_USAGE`
- `REQUEST_COST`
- `TENANT_QUOTA_USAGE`
- `TENANT_BUDGET_USAGE`
- `ROUTING_OUTCOME`

Legacy public operational tables are emptied by V5 and writes to the protected public copies are rejected.

### Validation

The supplied Maven output showed:

- compilation successful;
- `BudgetConcurrencyTest` passing;
- most existing tests passing;
- two `CostServiceTest` errors caused by the redundant mocked authenticated-tenant lookup and shared strict Mockito stubbing.

The corresponding source fixes are included in this project. Maven is not installed in the current execution workspace, so the corrected full suite could not be re-run here.

## Important separate security boundary

The `/admin/**` endpoints are intentionally configured as `permitAll` in the current MVP security configuration and are not part of tenant API-key authorization. They should be protected by a dedicated administrative authentication/authorization mechanism before production deployment. This update does not invent an admin credential model that is not present in the supplied project.
