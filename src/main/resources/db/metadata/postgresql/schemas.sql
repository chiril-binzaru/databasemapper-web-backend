-- params: none
SELECT schema_name
FROM information_schema.schemata
WHERE schema_name NOT IN ('information_schema', 'pg_catalog')
  AND schema_name NOT LIKE 'pg_%'
ORDER BY schema_name
