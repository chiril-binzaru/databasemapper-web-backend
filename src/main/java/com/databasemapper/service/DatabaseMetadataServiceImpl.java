package com.databasemapper.service;

import com.databasemapper.config.JdbcConnectionFactory;
import com.databasemapper.dto.DbColumnResponse;
import com.databasemapper.model.Connection;
import com.databasemapper.model.Database;
import com.databasemapper.model.enums.DatabaseType;
import com.databasemapper.repository.ConnectionRepository;
import com.databasemapper.repository.DatabaseRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class DatabaseMetadataServiceImpl implements DatabaseMetadataService {

    private final DatabaseRepository databaseRepository;
    private final ConnectionRepository connectionRepository;
    private final JdbcConnectionFactory jdbcConnectionFactory;

    @Override
    public List<String> getSchemas(Long databaseId) {
        Database database = requireDatabase(databaseId);
        Connection connection = requireActiveConnection(databaseId);

        try (java.sql.Connection conn = jdbcConnectionFactory.openConnection(database, connection)) {
            return switch (database.getDatabaseType()) {
                case POSTGRESQL, SQL_SERVER -> querySchemasSql(conn);
                case MYSQL                  -> queryCatalogsMeta(conn);
                case ORACLE                 -> querySchemasMeta(conn);
            };
        } catch (SQLException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Failed to fetch schemas from database: " + e.getMessage());
        }
    }

    @Override
    public List<String> getTables(Long databaseId, String schemaName) {
        Database database = requireDatabase(databaseId);
        Connection connection = requireActiveConnection(databaseId);

        try (java.sql.Connection conn = jdbcConnectionFactory.openConnection(database, connection)) {
            return switch (database.getDatabaseType()) {
                case POSTGRESQL, SQL_SERVER -> queryTablesSql(conn, schemaName);
                case MYSQL                  -> queryTablesMeta(conn, schemaName, true);
                case ORACLE                 -> queryTablesMeta(conn, schemaName, false);
            };
        } catch (SQLException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Failed to fetch tables from database: " + e.getMessage());
        }
    }

    @Override
    public List<DbColumnResponse> getColumns(Long databaseId, String schemaName, String tableName) {
        Database database = requireDatabase(databaseId);
        Connection connection = requireActiveConnection(databaseId);

        try (java.sql.Connection conn = jdbcConnectionFactory.openConnection(database, connection)) {
            return switch (database.getDatabaseType()) {
                case POSTGRESQL, SQL_SERVER -> queryColumnsSql(conn, schemaName, tableName);
                case MYSQL                  -> queryColumnsMeta(conn, schemaName, tableName, true);
                case ORACLE                 -> queryColumnsMeta(conn, schemaName, tableName, false);
            };
        } catch (SQLException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Failed to fetch columns from database: " + e.getMessage());
        }
    }

    // --- information_schema-based queries (PostgreSQL, SQL Server) ---

    private List<String> querySchemasSql(java.sql.Connection conn) throws SQLException {
        String sql = """
                SELECT schema_name
                FROM information_schema.schemata
                WHERE schema_name NOT IN ('information_schema', 'pg_catalog')
                  AND schema_name NOT LIKE 'pg_%'
                ORDER BY schema_name
                """;
        return queryStringColumn(conn, sql);
    }

    private List<String> queryTablesSql(java.sql.Connection conn, String schemaName) throws SQLException {
        String sql = """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = ?
                  AND table_type = 'BASE TABLE'
                ORDER BY table_name
                """;
        return queryStringColumn(conn, sql, schemaName);
    }

    private List<DbColumnResponse> queryColumnsSql(java.sql.Connection conn, String schemaName, String tableName) throws SQLException {
        String sql = """
                SELECT c.column_name, c.data_type, c.is_nullable, c.ordinal_position, c.column_default,
                       CASE WHEN kcu.column_name IS NOT NULL THEN true ELSE false END AS is_primary_key
                FROM information_schema.columns c
                LEFT JOIN information_schema.table_constraints tc
                    ON tc.table_schema = c.table_schema
                   AND tc.table_name   = c.table_name
                   AND tc.constraint_type = 'PRIMARY KEY'
                LEFT JOIN information_schema.key_column_usage kcu
                    ON kcu.constraint_name = tc.constraint_name
                   AND kcu.table_schema    = c.table_schema
                   AND kcu.table_name      = c.table_name
                   AND kcu.column_name     = c.column_name
                WHERE c.table_schema = ?
                  AND c.table_name   = ?
                ORDER BY c.ordinal_position
                """;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, schemaName);
            stmt.setString(2, tableName);
            try (ResultSet rs = stmt.executeQuery()) {
                List<DbColumnResponse> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(DbColumnResponse.builder()
                            .name(rs.getString("column_name"))
                            .dataType(rs.getString("data_type"))
                            .nullable("YES".equals(rs.getString("is_nullable")))
                            .ordinalPosition(rs.getInt("ordinal_position"))
                            .columnDefault(rs.getString("column_default"))
                            .primaryKey(rs.getBoolean("is_primary_key"))
                            .build());
                }
                return result;
            }
        }
    }

    // --- JDBC DatabaseMetaData-based queries (MySQL, Oracle) ---

    private List<String> queryCatalogsMeta(java.sql.Connection conn) throws SQLException {
        List<String> result = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getCatalogs()) {
            while (rs.next()) result.add(rs.getString("TABLE_CAT"));
        }
        return result;
    }

    private List<String> querySchemasMeta(java.sql.Connection conn) throws SQLException {
        List<String> result = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getSchemas()) {
            while (rs.next()) result.add(rs.getString("TABLE_SCHEM"));
        }
        return result;
    }

    private List<String> queryTablesMeta(java.sql.Connection conn, String schemaName, boolean isCatalog) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = isCatalog ? schemaName : null;
        String schema  = isCatalog ? null : schemaName;
        List<String> result = new ArrayList<>();
        try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) result.add(rs.getString("TABLE_NAME"));
        }
        return result;
    }

    private List<DbColumnResponse> queryColumnsMeta(java.sql.Connection conn, String schemaName, String tableName, boolean isCatalog) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        String catalog = isCatalog ? schemaName : null;
        String schema  = isCatalog ? null : schemaName;

        Set<String> pkColumns = new HashSet<>();
        try (ResultSet pkRs = meta.getPrimaryKeys(catalog, schema, tableName)) {
            while (pkRs.next()) pkColumns.add(pkRs.getString("COLUMN_NAME"));
        }

        List<DbColumnResponse> result = new ArrayList<>();
        try (ResultSet rs = meta.getColumns(catalog, schema, tableName, "%")) {
            int position = 0;
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                result.add(DbColumnResponse.builder()
                        .name(columnName)
                        .dataType(rs.getString("TYPE_NAME"))
                        .nullable(rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable)
                        .ordinalPosition(++position)
                        .columnDefault(rs.getString("COLUMN_DEF"))
                        .primaryKey(pkColumns.contains(columnName))
                        .build());
            }
        }
        return result;
    }

    // --- shared utility ---

    private List<String> queryStringColumn(java.sql.Connection conn, String sql, String... params) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) stmt.setString(i + 1, params[i]);
            try (ResultSet rs = stmt.executeQuery()) {
                List<String> result = new ArrayList<>();
                while (rs.next()) result.add(rs.getString(1));
                return result;
            }
        }
    }

    private Database requireDatabase(Long databaseId) {
        return databaseRepository.findById(databaseId)
                .orElseThrow(() -> new EntityNotFoundException("Database not found with id: " + databaseId));
    }

    private Connection requireActiveConnection(Long databaseId) {
        return connectionRepository.findActiveByDatabaseId(databaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No active connection found for database: " + databaseId));
    }
}
