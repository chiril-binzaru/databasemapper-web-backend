-- Written from the documented Oracle data-dictionary views; NOT verified against a live Oracle instance.
-- params: 1 = schema name (owner), 2 = table name
-- nullable is normalised from Oracle's 'Y'/'N' to the 'YES'/'NO' the shared mapper expects.
SELECT c.column_name                                                AS column_name,
       c.data_type                                                  AS data_type,
       CASE WHEN c.nullable = 'Y' THEN 'YES' ELSE 'NO' END          AS is_nullable,
       c.column_id                                                  AS ordinal_position,
       c.data_default                                               AS column_default,
       CASE WHEN EXISTS (SELECT 1
                         FROM all_constraints pk
                         JOIN all_cons_columns pkc
                           ON pkc.owner = pk.owner
                          AND pkc.constraint_name = pk.constraint_name
                         WHERE pk.constraint_type = 'P'
                           AND pk.owner = c.owner
                           AND pk.table_name = c.table_name
                           AND pkc.column_name = c.column_name)
            THEN 1 ELSE 0 END                                       AS is_primary_key
FROM all_tab_columns c
WHERE c.owner = ?
  AND c.table_name = ?
  AND c.column_id IS NOT NULL
ORDER BY c.column_id
