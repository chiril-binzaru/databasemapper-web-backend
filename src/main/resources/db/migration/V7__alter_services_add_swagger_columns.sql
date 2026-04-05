ALTER TABLE services
    ADD COLUMN last_fetched_swagger JSONB,
    ADD COLUMN committed_swagger    JSONB;
