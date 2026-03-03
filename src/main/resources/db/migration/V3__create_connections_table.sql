CREATE TABLE connections (
    connection_id BIGSERIAL PRIMARY KEY,
    database_id BIGINT NOT NULL,
    port INTEGER NOT NULL,
    username VARCHAR(255),
    password VARCHAR(255),
    connection_string VARCHAR(255),
    connection_mode VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_connections_database_id FOREIGN KEY (database_id) REFERENCES databases(database_id)
);