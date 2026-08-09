-- Written from the documented Oracle data-dictionary views; NOT verified against a live Oracle instance.
-- params: 1 = schema name (owner), 2 = table name
-- r_owner / r_constraint_name lead to the referenced key, so cross-schema references resolve;
-- key columns are paired by position, so composite keys produce one reference per column.
SELECT fkc.column_name    AS column_name,
       fk.constraint_name AS constraint_name,
       pk.owner           AS referenced_schema,
       pk.table_name      AS referenced_table,
       pkc.column_name    AS referenced_column
FROM all_constraints fk
JOIN all_cons_columns fkc
  ON fkc.owner = fk.owner
 AND fkc.constraint_name = fk.constraint_name
JOIN all_constraints pk
  ON pk.owner = fk.r_owner
 AND pk.constraint_name = fk.r_constraint_name
JOIN all_cons_columns pkc
  ON pkc.owner = pk.owner
 AND pkc.constraint_name = pk.constraint_name
 AND pkc.position = fkc.position
WHERE fk.constraint_type = 'R'
  AND fk.owner = ?
  AND fk.table_name = ?
ORDER BY fk.constraint_name, fkc.position
