package com.databasemapper.service.metadata;

import com.databasemapper.model.enums.DatabaseType;
import org.springframework.stereotype.Component;

@Component
public class OracleMetadataProvider extends AbstractDatabaseMetadataProvider {

    public OracleMetadataProvider() {
        super("oracle");
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.ORACLE;
    }
}
