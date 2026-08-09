-- params: none
-- All databases are returned unfiltered, including MySQL's own system databases.
SELECT schema_name
FROM information_schema.schemata
ORDER BY schema_name
