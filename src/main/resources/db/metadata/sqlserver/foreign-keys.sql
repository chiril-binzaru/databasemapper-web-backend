-- params: 1 = schema name, 2 = table name
SELECT kcu.column_name    AS column_name,
       tc.constraint_name AS constraint_name,
       kcu2.table_schema  AS referenced_schema,
       kcu2.table_name    AS referenced_table,
       kcu2.column_name   AS referenced_column
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
  ON kcu.constraint_name = tc.constraint_name
 AND kcu.constraint_schema = tc.constraint_schema
JOIN information_schema.referential_constraints rc
  ON rc.constraint_name = tc.constraint_name
 AND rc.constraint_schema = tc.constraint_schema
JOIN information_schema.key_column_usage kcu2
  ON kcu2.constraint_name = rc.unique_constraint_name
 AND kcu2.constraint_schema = rc.unique_constraint_schema
 AND kcu2.ordinal_position = kcu.position_in_unique_constraint
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND tc.table_schema = ?
  AND tc.table_name = ?
ORDER BY kcu.ordinal_position
