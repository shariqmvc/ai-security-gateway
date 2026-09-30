ALTER TABLE ROUTING_HEALTH_PROFILE
    ADD COLUMN endpoint_id VARCHAR(255);

UPDATE ROUTING_HEALTH_PROFILE
SET endpoint_id = LOWER(provider::text) || '-default'
WHERE endpoint_id IS NULL;

ALTER TABLE ROUTING_HEALTH_PROFILE
    ALTER COLUMN endpoint_id SET NOT NULL;

DROP INDEX IF EXISTS uk_routing_health_provider_model;

ALTER TABLE ROUTING_HEALTH_PROFILE
    ADD CONSTRAINT uk_routing_health_provider_model_endpoint
    UNIQUE (provider, model, endpoint_id);
