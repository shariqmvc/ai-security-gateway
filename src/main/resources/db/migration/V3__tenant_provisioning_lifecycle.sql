ALTER TABLE TENANTS
    ADD COLUMN provisioning_started_at TIMESTAMP(6),
    ADD COLUMN provisioning_completed_at TIMESTAMP(6),
    ADD COLUMN provisioning_failure_reason VARCHAR(1000),
    ADD COLUMN provisioning_attempts INTEGER NOT NULL DEFAULT 0;
