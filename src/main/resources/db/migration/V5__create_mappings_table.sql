CREATE TABLE mappings (
    mapping_id BIGSERIAL PRIMARY KEY,
    endpoint_id BIGINT NOT NULL,
    response_model_name VARCHAR(255) NOT NULL,
    mapping_data JSONB,

    CONSTRAINT fk_mappings_endpoint_id FOREIGN KEY (endpoint_id) REFERENCES endpoints(endpoint_id)
);