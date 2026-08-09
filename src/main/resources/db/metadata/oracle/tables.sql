-- Written from the documented Oracle data-dictionary views; NOT verified against a live Oracle instance.
-- params: 1 = schema name (owner)
-- all_tables holds base tables only; views live in all_views.
SELECT t.table_name AS table_name
FROM all_tables t
WHERE t.owner = ?
ORDER BY t.table_name
