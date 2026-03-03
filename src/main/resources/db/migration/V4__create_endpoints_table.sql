CREATE TABLE endpoints (
    endpoint_id BIGSERIAL PRIMARY KEY,
    service_id BIGINT NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    endpoint_path VARCHAR(255) NOT NULL,

    CONSTRAINT fk_endpoints_service_id FOREIGN KEY (service_id) REFERENCES services(service_id)
);