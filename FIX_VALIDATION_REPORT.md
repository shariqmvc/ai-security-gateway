# Fix Validation Report

## Applied fixes

- `RateLimitFilter` bypasses tenant rate-limit/quota enforcement for platform principals.
- `InMemoryRateLimiterServiceImpl` is defensive against platform/null-tenant contexts.
- `EntitlementCache` rejects null cache keys safely.
- `EntitlementServiceImpl.getDto` explicitly rejects null tenant IDs.
- `SecurityConfig` disables HTTP Basic/form-login/logout.
- Spring Boot `UserDetailsServiceAutoConfiguration` is excluded.
- Added unit coverage for platform rate-limit bypass.
- Added unit coverage for entitlement-cache null safety.
- Removed the uploaded `.evn` secret file from the distributable ZIP.
- Added `.env.example` with placeholders.

## Environment limitation

Maven is not installed in the execution environment used to prepare this ZIP, and the uploaded project does not contain `mvnw`. Therefore a full `mvn clean test` build could not be executed here.

Run locally from the project directory:

```bash
mvn clean test
```

If Maven is available through IntelliJ, use the Maven tool window and execute:

`Lifecycle -> clean`

then:

`Lifecycle -> test`

## Expected regression

The previous failure:

`NullPointerException at EntitlementCache.get(EntitlementCache.java:17)`

must no longer occur for platform API requests.

Platform requests should proceed to authorization/controller handling. Tenant requests should continue through entitlement, rate-limit, and quota enforcement.
