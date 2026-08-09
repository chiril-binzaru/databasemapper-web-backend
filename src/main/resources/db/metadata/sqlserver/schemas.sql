-- params: none
SELECT schema_name
FROM information_schema.schemata
WHERE schema_name NOT IN ('INFORMATION_SCHEMA', 'sys', 'guest',
                          'db_owner', 'db_accessadmin', 'db_securityadmin', 'db_ddladmin',
                          'db_backupoperator', 'db_datareader', 'db_datawriter',
                          'db_denydatareader', 'db_denydatawriter')
ORDER BY schema_name
