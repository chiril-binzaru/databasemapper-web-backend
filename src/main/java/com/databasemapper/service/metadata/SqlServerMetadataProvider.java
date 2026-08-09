package com.databasemapper.service.metadata;

import com.databasemapper.model.enums.DatabaseType;
import org.springframework.stereotype.Component;

@Component
public class SqlServerMetadataProvider extends AbstractDatabaseMetadataProvider {

    public SqlServerMetadataProvider() {
        super("sqlserver");
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.SQL_SERVER;
    }
}
