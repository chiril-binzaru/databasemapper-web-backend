-- params: 1 = schema name, 2 = table name
SELECT c.column_name                                                   AS column_name,
       c.data_type                                                     AS data_type,
       c.is_nullable                                                   AS is_nullable,
       c.ordinal_position                                              AS ordinal_position,
       c.column_default                                                AS column_default,
       CASE WHEN kcu.column_name IS NOT NULL THEN true ELSE false END  AS is_primary_key
FROM information_schema.columns c
LEFT JOIN information_schema.table_constraints tc
       ON tc.table_schema = c.table_schema
      AND tc.table_name = c.table_name
      AND tc.constraint_type = 'PRIMARY KEY'
LEFT JOIN information_schema.key_column_usage kcu
       ON kcu.constraint_name = tc.constraint_name
      AND kcu.constraint_schema = tc.constraint_schema
      AND kcu.table_schema = c.table_schema
      AND kcu.table_name = c.table_name
      AND kcu.column_name = c.column_name
WHERE c.table_schema = ?
  AND c.table_name = ?
ORDER BY c.ordinal_position
