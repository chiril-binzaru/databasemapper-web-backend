package com.databasemapper.service.metadata;

import com.databasemapper.model.enums.DatabaseType;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

@Component
public class MySqlMetadataProvider extends AbstractDatabaseMetadataProvider {

    public MySqlMetadataProvider() {
        super("mysql");
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.MYSQL;
    }

    /**
     * MySQL has no schema level below the database: SCHEMA and DATABASE are synonyms. The database is
     * already fixed by the connection, so the one schema on offer is the connection's own catalog
     * rather than every database on the server. Falls back to the shipped query when the connection
     * carries no catalog, which happens when a connection string omits the database.
     */
    @Override
    public List<String> getSchemas(Connection conn) throws SQLException {
        String catalog = conn.getCatalog();
        if (catalog != null && !catalog.isBlank()) {
            return List.of(catalog);
        }
        return super.getSchemas(conn);
    }
}
