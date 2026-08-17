-- AegisAI tenant-isolation hardening.
-- Tenant operational data is authoritative only inside tenant_<uuid> schemas.
-- V1 created legacy public copies because the JPA entities are schema-neutral
-- and Hibernate validates against the default/public schema. They therefore
-- remain as empty compatibility structures, but are made non-authoritative:
--   1. Existing public operational rows are removed.
--   2. Writes to those public tables are rejected.
--   3. Tenant-scoped application services must set search_path to a tenant
--      schema before accessing the same entity names.
--
-- Platform/control-plane tables such as TENANTS, API_KEYS,
-- TENANT_ENTITLEMENTS, TENANT_ENTITLEMENT_FEATURES, ROUTING_OUTCOME and
-- ROUTING_HEALTH_PROFILE remain platform-scoped.

TRUNCATE TABLE
    REQUEST_AUDIT,
    TOKEN_VAULT,
    TOKEN_USAGE,
    REQUEST_COST,
    TENANT_BUDGET_USAGE,
    TENANT_QUOTA_USAGE;

CREATE OR REPLACE FUNCTION reject_public_tenant_operational_write()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'Tenant operational table % is not writable in public schema; establish tenant schema routing first',
        TG_TABLE_NAME;
END;
$$;

DROP TRIGGER IF EXISTS trg_reject_public_request_audit_write
    ON REQUEST_AUDIT;
CREATE TRIGGER trg_reject_public_request_audit_write
    BEFORE INSERT OR UPDATE OR DELETE ON REQUEST_AUDIT
    FOR EACH ROW EXECUTE FUNCTION reject_public_tenant_operational_write();

DROP TRIGGER IF EXISTS trg_reject_public_token_vault_write
    ON TOKEN_VAULT;
CREATE TRIGGER trg_reject_public_token_vault_write
    BEFORE INSERT OR UPDATE OR DELETE ON TOKEN_VAULT
    FOR EACH ROW EXECUTE FUNCTION reject_public_tenant_operational_write();

DROP TRIGGER IF EXISTS trg_reject_public_token_usage_write
    ON TOKEN_USAGE;
CREATE TRIGGER trg_reject_public_token_usage_write
    BEFORE INSERT OR UPDATE OR DELETE ON TOKEN_USAGE
    FOR EACH ROW EXECUTE FUNCTION reject_public_tenant_operational_write();

DROP TRIGGER IF EXISTS trg_reject_public_request_cost_write
    ON REQUEST_COST;
CREATE TRIGGER trg_reject_public_request_cost_write
    BEFORE INSERT OR UPDATE OR DELETE ON REQUEST_COST
    FOR EACH ROW EXECUTE FUNCTION reject_public_tenant_operational_write();

DROP TRIGGER IF EXISTS trg_reject_public_tenant_budget_usage_write
    ON TENANT_BUDGET_USAGE;
CREATE TRIGGER trg_reject_public_tenant_budget_usage_write
    BEFORE INSERT OR UPDATE OR DELETE ON TENANT_BUDGET_USAGE
    FOR EACH ROW EXECUTE FUNCTION reject_public_tenant_operational_write();

DROP TRIGGER IF EXISTS trg_reject_public_tenant_quota_usage_write
    ON TENANT_QUOTA_USAGE;
CREATE TRIGGER trg_reject_public_tenant_quota_usage_write
    BEFORE INSERT OR UPDATE OR DELETE ON TENANT_QUOTA_USAGE
    FOR EACH ROW EXECUTE FUNCTION reject_public_tenant_operational_write();
