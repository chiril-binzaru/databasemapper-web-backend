-- Written from the documented Oracle data-dictionary views; NOT verified against a live Oracle instance.
-- params: none
-- Schemas are returned unfiltered, including Oracle's own internal schemas.
SELECT u.username AS schema_name
FROM all_users u
ORDER BY u.username
