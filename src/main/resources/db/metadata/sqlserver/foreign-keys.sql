-- params: 1 = schema name, 2 = table name
-- SQL Server does not implement information_schema.key_column_usage.position_in_unique_constraint,
-- the ISO column used to pair a foreign key's columns with the referenced key's, so the referenced
-- side is resolved through the sys catalog views instead. sys.foreign_key_columns already yields one
-- row per key column pair, which keeps composite keys from multiplying and lets the referenced table
-- live in another schema.
SELECT pc.name                   AS column_name,
       fk.name                   AS constraint_name,
       SCHEMA_NAME(rt.schema_id) AS referenced_schema,
       rt.name                   AS referenced_table,
       rc.name                   AS referenced_column
FROM sys.foreign_keys fk
JOIN sys.tables pt
  ON pt.object_id = fk.parent_object_id
JOIN sys.foreign_key_columns fkc
  ON fkc.constraint_object_id = fk.object_id
JOIN sys.columns pc
  ON pc.object_id = fkc.parent_object_id
 AND pc.column_id = fkc.parent_column_id
JOIN sys.tables rt
  ON rt.object_id = fk.referenced_object_id
JOIN sys.columns rc
  ON rc.object_id = fkc.referenced_object_id
 AND rc.column_id = fkc.referenced_column_id
WHERE SCHEMA_NAME(pt.schema_id) = ?
  AND pt.name = ?
ORDER BY fk.name, fkc.constraint_column_id
