CREATE TABLE databases (
    database_id BIGSERIAL PRIMARY KEY,
    database_type VARCHAR(50) NOT NULL,
    database_host VARCHAR(255) NOT NULL,
    database_name VARCHAR(255) NOT NULL
);