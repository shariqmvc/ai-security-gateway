# Phase 7.3 — Provisioning Expansion

Implemented against the uploaded latest project snapshot.

## Provisioning lifecycle

REQUESTED -> PROVISIONING -> VALIDATING -> ACTIVE

Provisioning failures persist the tenant as FAILED and preserve failure diagnostics. FAILED tenants can be retried.

## Provisioning responsibilities

- tenant provisioning orchestration
- plan entitlement provisioning
- initial tenant API credential provisioning
- validation/activation gate
- provisioning attempts and timestamps
- failure reason persistence
- provisioning status endpoint
- provisioning retry endpoint
- API-key rotation
- API-key revocation
- tenant-aware API-key authentication (tenant must be ACTIVE)
- concurrency protection for API-key rotation

## Endpoints

GET  /admin/tenants/{tenantId}/provisioning
POST /admin/tenants/{tenantId}/provisioning/retry
POST /admin/tenants/{tenantId}/api-keys?clientName=...
DELETE /admin/tenants/{tenantId}/api-keys/{apiKeyId}

## Database

V3__tenant_provisioning_lifecycle.sql adds provisioning lifecycle metadata to TENANTS.

## Security

Raw API-key secrets are generated with SecureRandom and are not recoverable after persistence. Initial provisioning is idempotent and retains an existing active key. Rotation invalidates existing active keys before creating the replacement.

## Scope boundary

The current repository has no tenant-user/identity model, admin-user repository, password/identity lifecycle, department model, application/service-account model, or tenant schema provisioning abstraction. Those baseline capabilities require a separate identity/organizational persistence contract and were not fabricated into this phase.

## Verification

The execution environment contains Java 21 but does not contain Maven or a Maven wrapper, so Maven compilation/test execution could not be performed here. Run locally:

mvn clean test

Focused validation:

mvn -Dtest=TenantServiceImplTest,TenantProvisioningServiceTest,TenantProvisioningTransactionTest,TenantProvisioningLifecycleIntegrationTest,ApiKeyServiceImplTest test
