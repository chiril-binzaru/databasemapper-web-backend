package com.databasemapper.service.metadata;

import com.databasemapper.dto.DbColumnResponse;
import com.databasemapper.dto.ForeignKeyReferenceDto;
import com.databasemapper.model.enums.DatabaseType;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Providers receive an already-open connection: they must neither open nor close it,
 * and never access repositories. SQLException translation is the caller's job.
 * <p>
 * Every vendor answers the same three questions with the same shape; only the SQL differs,
 * and that SQL lives in {@code db/metadata/{vendor}/} on the classpath.
 */
public abstract class AbstractDatabaseMetadataProvider {

    private final MetadataQuery schemasQuery;
    private final MetadataQuery tablesQuery;
    private final MetadataQuery columnsQuery;
    private final MetadataQuery foreignKeysQuery;

    protected AbstractDatabaseMetadataProvider(String vendorDirectory) {
        this.schemasQuery = load(vendorDirectory, "schemas.sql");
        this.tablesQuery = load(vendorDirectory, "tables.sql");
        this.columnsQuery = load(vendorDirectory, "columns.sql");
        this.foreignKeysQuery = load(vendorDirectory, "foreign-keys.sql");
    }

    public abstract DatabaseType getDatabaseType();

    public List<String> getSchemas(Connection conn) throws SQLException {
        return queryStrings(conn, schemasQuery);
    }

    public List<String> getTables(Connection conn, String schemaName) throws SQLException {
        return queryStrings(conn, tablesQuery, schemaName);
    }

    public List<DbColumnResponse> getColumns(Connection conn, String schemaName, String tableName) throws SQLException {
        Map<String, List<ForeignKeyReferenceDto>> foreignKeysByColumn =
                queryForeignKeys(conn, foreignKeysQuery, schemaName, tableName);
        return queryColumns(conn, columnsQuery, foreignKeysByColumn, schemaName, tableName);
    }

    protected final List<String> queryStrings(Connection conn, MetadataQuery query, Object... params) throws SQLException {
        checkParameterCount(query, params);
        try (PreparedStatement stmt = conn.prepareStatement(query.sql())) {
            bind(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                List<String> result = new ArrayList<>();
                while (rs.next()) {
                    result.add(rs.getString(1));
                }
                return result;
            }
        }
    }

    protected final List<DbColumnResponse> queryColumns(Connection conn,
                                                        MetadataQuery query,
                                                        Map<String, List<ForeignKeyReferenceDto>> foreignKeysByColumn,
                                                        Object... params) throws SQLException {
        checkParameterCount(query, params);
        try (PreparedStatement stmt = conn.prepareStatement(query.sql())) {
            bind(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                List<DbColumnResponse> result = new ArrayList<>();
                while (rs.next()) {
                    String columnName = rs.getString("column_name");
                    result.add(DbColumnResponse.builder()
                            .name(columnName)
                            .dataType(rs.getString("data_type"))
                            .nullable("YES".equals(rs.getString("is_nullable")))
                            .ordinalPosition(rs.getInt("ordinal_position"))
                            .columnDefault(rs.getString("column_default"))
                            .primaryKey(rs.getBoolean("is_primary_key"))
                            .foreignKeys(foreignKeysByColumn.getOrDefault(columnName, List.of()))
                            .build());
                }
                return result;
            }
        }
    }

    protected final Map<String, List<ForeignKeyReferenceDto>> queryForeignKeys(Connection conn,
                                                                              MetadataQuery query,
                                                                              Object... params) throws SQLException {
        checkParameterCount(query, params);
        try (PreparedStatement stmt = conn.prepareStatement(query.sql())) {
            bind(stmt, params);
            try (ResultSet rs = stmt.executeQuery()) {
                Map<String, List<ForeignKeyReferenceDto>> result = new LinkedHashMap<>();
                while (rs.next()) {
                    String columnName = rs.getString("column_name");
                    result.computeIfAbsent(columnName, ignored -> new ArrayList<>())
                            .add(ForeignKeyReferenceDto.builder()
                                    .constraintName(rs.getString("constraint_name"))
                                    .referencedSchema(rs.getString("referenced_schema"))
                                    .referencedTable(rs.getString("referenced_table"))
                                    .referencedColumn(rs.getString("referenced_column"))
                                    .build());
                }
                return result;
            }
        }
    }

    private static void bind(PreparedStatement stmt, Object... params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            stmt.setObject(i + 1, params[i]);
        }
    }

    private static void checkParameterCount(MetadataQuery query, Object... params) {
        int placeholders = countPlaceholders(query.sql());
        if (placeholders != params.length) {
            throw new IllegalStateException("Query %s declares %d parameter placeholder(s) but %d argument(s) were bound"
                    .formatted(query.resourcePath(), placeholders, params.length));
        }
    }

    private static int countPlaceholders(String sql) {
        int count = 0;
        boolean inLiteral = false;
        boolean inLineComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (inLineComment) {
                if (c == '\n') inLineComment = false;
            } else if (inLiteral) {
                if (c == '\'') inLiteral = false;
            } else if (c == '\'') {
                inLiteral = true;
            } else if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                inLineComment = true;
            } else if (c == '?') {
                count++;
            }
        }
        return count;
    }

    private static MetadataQuery load(String vendorDirectory, String fileName) {
        String resourcePath = "db/metadata/%s/%s".formatted(vendorDirectory, fileName);
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            throw new IllegalStateException("Metadata SQL resource not found on classpath: " + resourcePath);
        }
        try (InputStream in = resource.getInputStream()) {
            return new MetadataQuery(resourcePath, new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read metadata SQL resource: " + resourcePath, e);
        }
    }

    protected record MetadataQuery(String resourcePath, String sql) {
    }
}
