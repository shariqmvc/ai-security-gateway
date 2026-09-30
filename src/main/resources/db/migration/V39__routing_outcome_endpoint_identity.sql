ALTER TABLE ROUTING_OUTCOME
    ADD COLUMN endpoint_id VARCHAR(255);

UPDATE ROUTING_OUTCOME
SET endpoint_id = LOWER(provider::text) || '-default'
WHERE endpoint_id IS NULL;

ALTER TABLE ROUTING_OUTCOME
    ALTER COLUMN endpoint_id SET NOT NULL;

DROP INDEX IF EXISTS idx_routing_outcome_provider_model;

CREATE INDEX idx_routing_outcome_provider_model_endpoint
    ON ROUTING_OUTCOME(provider, model, endpoint_id);
