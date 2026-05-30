ALTER TABLE endpoints
    ADD CONSTRAINT uq_endpoints_service_path_method UNIQUE (service_id, endpoint_path, http_method);
