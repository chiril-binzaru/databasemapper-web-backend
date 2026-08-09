-- params: 1 = schema name (MySQL database), 2 = table name
SELECT c.column_name                                          AS column_name,
       c.data_type                                            AS data_type,
       c.is_nullable                                          AS is_nullable,
       c.ordinal_position                                     AS ordinal_position,
       c.column_default                                       AS column_default,
       CASE WHEN c.column_key = 'PRI' THEN 1 ELSE 0 END       AS is_primary_key
FROM information_schema.columns c
WHERE c.table_schema = ?
  AND c.table_name = ?
ORDER BY c.ordinal_position
