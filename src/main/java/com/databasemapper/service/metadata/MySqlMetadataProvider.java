package com.databasemapper.service.metadata;

import com.databasemapper.model.enums.DatabaseType;
import org.springframework.stereotype.Component;

@Component
public class MySqlMetadataProvider extends AbstractDatabaseMetadataProvider {

    public MySqlMetadataProvider() {
        super("mysql");
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.MYSQL;
    }
}
