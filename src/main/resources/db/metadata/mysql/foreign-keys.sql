-- params: 1 = schema name (MySQL database), 2 = table name
-- MySQL exposes the referenced side directly on key_column_usage, one row per key column,
-- so no join back to referential_constraints is needed for cross-schema or composite keys.
SELECT kcu.column_name                 AS column_name,
       kcu.constraint_name             AS constraint_name,
       kcu.referenced_table_schema     AS referenced_schema,
       kcu.referenced_table_name       AS referenced_table,
       kcu.referenced_column_name      AS referenced_column
FROM information_schema.key_column_usage kcu
WHERE kcu.table_schema = ?
  AND kcu.table_name = ?
  AND kcu.referenced_table_name IS NOT NULL
ORDER BY kcu.constraint_name, kcu.ordinal_position
