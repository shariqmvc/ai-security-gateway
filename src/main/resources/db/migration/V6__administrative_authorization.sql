-- AegisAI 7.6 Administrative Authorization Foundation
--
-- Tenant API keys carry a tenant-scoped role. Existing keys remain
-- least-privileged TENANT_USER keys until explicitly promoted by a
-- platform administrator.
ALTER TABLE API_KEYS
    ADD COLUMN IF NOT EXISTS role VARCHAR(64) NOT NULL DEFAULT 'TENANT_USER';

CREATE INDEX IF NOT EXISTS idx_api_keys_tenant_role
    ON API_KEYS (tenant_id, role);

COMMENT ON COLUMN API_KEYS.role IS
    'Authorization role. Tenant roles never grant access outside the owning tenant.';
