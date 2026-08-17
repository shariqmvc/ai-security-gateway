# Platform/Tenant Security Boundary Fix

## Problem fixed

Platform principals intentionally have `tenantId = null`. The request pipeline was still applying tenant rate limiting to platform requests. That caused:

`RateLimitFilter -> InMemoryRateLimiterServiceImpl -> EntitlementServiceImpl -> EntitlementCache -> ConcurrentHashMap.get(null)`

and resulted in:

`NullPointerException: Cannot invoke "Object.hashCode()" because "key" is null`

## Changes

### 1. `RateLimitFilter`

Platform principals now bypass tenant rate limiting and tenant daily quota because those controls are tenant-scoped.

### 2. `InMemoryRateLimiterServiceImpl`

Added defense-in-depth handling:
- platform principal -> allowed without tenant entitlement lookup
- non-platform principal with null tenant ID -> explicit `IllegalArgumentException`

### 3. `EntitlementCache`

Cache operations are null-safe. A null tenant ID can no longer reach `ConcurrentHashMap`.

### 4. `EntitlementServiceImpl`

`getDto(null)` now fails explicitly with an `IllegalArgumentException` rather than producing an infrastructure-level `NullPointerException`.

### 5. `SecurityConfig`

Disabled HTTP Basic, form login, and logout because AegisAI authenticates requests through the API-key `AuthenticationFilter`.

### 6. Spring Security default user

Excluded `UserDetailsServiceAutoConfiguration` so Spring Boot no longer creates/logs the development-only generated password. This removes an unrelated authentication mechanism from the gateway.

## Security invariant

Platform principals:
- have `tenantId = null`
- may access explicitly authorized platform/control-plane APIs
- do not inherit tenant business-data access
- must not execute tenant entitlement, quota, or tenant rate-limit logic

Tenant principals:
- must have a non-null tenant ID
- are subject to tenant entitlement, quota, and rate-limit controls
- require exact tenant matching for tenant-scoped authorization

## Validation

Run from the project root:

```bash
mvn clean test
```

Then start the application with `AEGIS_PLATFORM_API_KEY` configured.

Verify:

1. Platform API with valid platform key does not produce the previous NPE.
2. Missing API key -> 401.
3. Tenant API key -> platform admin endpoint -> 403.
4. Platform owner -> authorized admin endpoint -> normal 2xx/validation response.
5. Tenant API traffic still executes rate-limit and daily quota checks.
6. No generated Spring Security development password appears in startup logs.
