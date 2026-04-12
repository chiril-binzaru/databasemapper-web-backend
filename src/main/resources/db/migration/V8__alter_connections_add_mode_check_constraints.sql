ALTER TABLE connections
    ADD CONSTRAINT chk_connections_parameters_mode
        CHECK (connection_mode != 'PARAMETERS' OR (username IS NOT NULL AND password IS NOT NULL AND port IS NOT NULL)),
    ADD CONSTRAINT chk_connections_connection_string_mode
        CHECK (connection_mode != 'CONNECTION_STRING' OR connection_string IS NOT NULL);
