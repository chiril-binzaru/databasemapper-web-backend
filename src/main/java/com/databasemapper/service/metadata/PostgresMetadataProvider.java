package com.databasemapper.service.metadata;

import com.databasemapper.model.enums.DatabaseType;
import org.springframework.stereotype.Component;

@Component
public class PostgresMetadataProvider extends AbstractDatabaseMetadataProvider {

    public PostgresMetadataProvider() {
        super("postgresql");
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.POSTGRESQL;
    }
}
