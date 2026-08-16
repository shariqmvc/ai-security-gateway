ALTER TABLE TENANTS
    ADD COLUMN schema_name VARCHAR(63);

CREATE UNIQUE INDEX uk_tenants_schema_name
    ON TENANTS (schema_name);