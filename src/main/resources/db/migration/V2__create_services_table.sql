CREATE TABLE services (
    service_id BIGSERIAL PRIMARY KEY,
    database_id BIGINT NOT NULL,
    service_name VARCHAR(255) NOT NULL,
    service_base_url VARCHAR(255) NOT NULL,
    swagger_endpoint VARCHAR(255),

    CONSTRAINT fk_services_database_id FOREIGN KEY (database_id) REFERENCES databases(database_id)
);