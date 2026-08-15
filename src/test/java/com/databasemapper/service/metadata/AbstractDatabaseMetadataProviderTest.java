package com.databasemapper.service.metadata;

import com.databasemapper.dto.DbColumnResponse;
import com.databasemapper.dto.ForeignKeyReferenceDto;
import com.databasemapper.model.enums.DatabaseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the shared template/helper logic in {@link AbstractDatabaseMetadataProvider}.
 * Lives in the production package because {@code MetadataQuery} and the query helpers are protected.
 * All JDBC is mocked - no database is contacted.
 */
@ExtendWith(MockitoExtension.class)
class AbstractDatabaseMetadataProviderTest {

    private static final String SCHEMAS_RESOURCE = "db/metadata/postgresql/schemas.sql";
    private static final String TABLES_RESOURCE = "db/metadata/postgresql/tables.sql";
    private static final String COLUMNS_RESOURCE = "db/metadata/postgresql/columns.sql";
    private static final String FOREIGN_KEYS_RESOURCE = "db/metadata/postgresql/foreign-keys.sql";

    @Mock
    private Connection conn;

    @Mock
    private PreparedStatement stmt;

    @Mock
    private ResultSet rs;

    private final PostgresMetadataProvider provider = new PostgresMetadataProvider();

    // --- A. SQL loading -------------------------------------------------------------------------

    @Test
    void constructorThrowsIllegalStateExceptionNamingTheMissingResourceForUnknownVendorDirectory() {
        IllegalStateException ex = assertThrows(IllegalStateException.class, BogusVendorProvider::new);

        assertTrue(ex.getMessage().contains("db/metadata/bogus-vendor/schemas.sql"),
                "message should name the missing resource path but was: " + ex.getMessage());
    }

    // --- B. Placeholder guard -------------------------------------------------------------------

    @Test
    void queryHelperThrowsIllegalStateExceptionNamingTheQueryWhenTooFewArgumentsAreBound() {
        TestProvider testProvider = new TestProvider();
        AbstractDatabaseMetadataProvider.MetadataQuery tablesQuery =
                new AbstractDatabaseMetadataProvider.MetadataQuery(TABLES_RESOURCE, readSql(TABLES_RESOURCE));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> testProvider.runQueryStrings(conn, tablesQuery));

        assertTrue(ex.getMessage().contains(TABLES_RESOURCE),
                "message should name the offending query but was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("declares 1 parameter placeholder(s) but 0 argument(s) were bound"),
                "message should report both counts but was: " + ex.getMessage());
    }

    @Test
    void queryHelperThrowsIllegalStateExceptionWhenTooManyArgumentsAreBound() throws SQLException {
        TestProvider testProvider = new TestProvider();
        AbstractDatabaseMetadataProvider.MetadataQuery query =
                new AbstractDatabaseMetadataProvider.MetadataQuery("test/two-params.sql",
                        "SELECT 1 FROM t WHERE a = ? AND b = ?");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> testProvider.runQueryStrings(conn, query, "a", "b", "c"));

        assertTrue(ex.getMessage().contains("test/two-params.sql"),
                "message should name the offending query but was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("declares 2 parameter placeholder(s) but 3 argument(s) were bound"),
                "message should report both counts but was: " + ex.getMessage());
        verify(conn, never()).prepareStatement(any(String.class));
    }

    @Test
    void placeholderCountIgnoresQuestionMarksInLineCommentsAndStringLiterals() {
        TestProvider testProvider = new TestProvider();
        String sql = """
                -- params: 1 = schema name. Is this a placeholder? No? Definitely not.
                SELECT '?' AS literal_question,
                       'it''s ? tricky' AS escaped_literal
                FROM t
                WHERE name = ?
                """;
        AbstractDatabaseMetadataProvider.MetadataQuery query =
                new AbstractDatabaseMetadataProvider.MetadataQuery("test/tricky.sql", sql);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> testProvider.runQueryStrings(conn, query));

        assertTrue(ex.getMessage().contains("declares 1 parameter placeholder(s) but 0 argument(s) were bound"),
                "only the real placeholder should be counted but message was: " + ex.getMessage());
    }

    @Test
    void placeholderCountAcceptsTheSingleRealPlaceholderInCommentAndLiteralHeavySql() throws SQLException {
        TestProvider testProvider = new TestProvider();
        String sql = """
                -- params: 1 = schema name. Is this a placeholder? No? Definitely not.
                SELECT '?' AS literal_question,
                       'it''s ? tricky' AS escaped_literal
                FROM t
                WHERE name = ?
                """;
        AbstractDatabaseMetadataProvider.MetadataQuery query =
                new AbstractDatabaseMetadataProvider.MetadataQuery("test/tricky.sql", sql);
        when(conn.prepareStatement(sql)).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        List<String> result = testProvider.runQueryStrings(conn, query, "public");

        assertEquals(List.of(), result);
        verify(stmt).setObject(1, "public");
    }

    @Test
    void placeholderCountIgnoresQuestionMarksInMultiLineBlockComments() {
        TestProvider testProvider = new TestProvider();
        String sql = """
                /* params: 1 = schema name.
                   Is this a placeholder? No.
                   Neither is this ? one. */
                SELECT 1 AS one
                FROM t
                WHERE name = ?
                """;
        AbstractDatabaseMetadataProvider.MetadataQuery query =
                new AbstractDatabaseMetadataProvider.MetadataQuery("test/block-comment.sql", sql);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> testProvider.runQueryStrings(conn, query));

        assertTrue(ex.getMessage().contains("declares 1 parameter placeholder(s) but 0 argument(s) were bound"),
                "only the real placeholder should be counted but message was: " + ex.getMessage());
    }

    @Test
    void placeholderCountIgnoresQuestionMarksInABlockCommentContainingAQuote() {
        TestProvider testProvider = new TestProvider();
        String sql = """
                /* It's ? a comment, and the apostrophe above must not open a literal.
                   Nor should this ? count. */
                SELECT 1 AS one
                FROM t
                WHERE name = ?
                  AND owner = ?
                """;
        AbstractDatabaseMetadataProvider.MetadataQuery query =
                new AbstractDatabaseMetadataProvider.MetadataQuery("test/block-comment-quote.sql", sql);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> testProvider.runQueryStrings(conn, query));

        assertTrue(ex.getMessage().contains("declares 2 parameter placeholder(s) but 0 argument(s) were bound"),
                "only the two real placeholders should be counted but message was: " + ex.getMessage());
    }

    @Test
    void placeholderCountIgnoresQuestionMarksInAnInlineBlockCommentAndResumesCountingAfterIt() {
        TestProvider testProvider = new TestProvider();
        String sql = "SELECT 1 FROM t WHERE a = ? /* not a placeholder: ? */ AND b = ?";
        AbstractDatabaseMetadataProvider.MetadataQuery query =
                new AbstractDatabaseMetadataProvider.MetadataQuery("test/inline-block-comment.sql", sql);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> testProvider.runQueryStrings(conn, query));

        assertTrue(ex.getMessage().contains("declares 2 parameter placeholder(s) but 0 argument(s) were bound"),
                "counting should resume after the block comment but message was: " + ex.getMessage());
    }

    @Test
    void placeholderCountTreatsABlockCommentOpenerInsideALiteralAsText() {
        TestProvider testProvider = new TestProvider();
        String sql = "SELECT '/* ? not a comment' AS literal FROM t WHERE a = ?";
        AbstractDatabaseMetadataProvider.MetadataQuery query =
                new AbstractDatabaseMetadataProvider.MetadataQuery("test/block-comment-in-literal.sql", sql);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> testProvider.runQueryStrings(conn, query));

        assertTrue(ex.getMessage().contains("declares 1 parameter placeholder(s) but 0 argument(s) were bound"),
                "only the real placeholder should be counted but message was: " + ex.getMessage());
    }

    @Test
    void placeholderCountAcceptsTheRealPlaceholdersInBlockCommentHeavySql() throws SQLException {
        TestProvider testProvider = new TestProvider();
        String sql = """
                /* params: 1 = schema name, 2 = table name.
                   Is this a placeholder? No. It's ? not one. */
                SELECT 1 AS one
                FROM t
                WHERE schema_name = ? -- still not a ? here
                  AND table_name = ?
                """;
        AbstractDatabaseMetadataProvider.MetadataQuery query =
                new AbstractDatabaseMetadataProvider.MetadataQuery("test/block-comment-ok.sql", sql);
        when(conn.prepareStatement(sql)).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        List<String> result = testProvider.runQueryStrings(conn, query, "public", "orders");

        assertEquals(List.of(), result);
        verify(stmt).setObject(1, "public");
        verify(stmt).setObject(2, "orders");
    }

    // --- C. Execution and mapping ---------------------------------------------------------------

    @Test
    void getSchemasExecutesSchemasQueryWithoutBindingsAndReturnsFirstColumn() throws SQLException {
        String schemasSql = readSql(SCHEMAS_RESOURCE);
        when(conn.prepareStatement(schemasSql)).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString(1)).thenReturn("public", "sales");

        List<String> schemas = provider.getSchemas(conn);

        assertEquals(List.of("public", "sales"), schemas);
        verify(stmt, never()).setObject(anyInt(), any());
        verify(conn).prepareStatement(schemasSql);
    }

    @Test
    void getTablesBindsSchemaNameAsFirstParameterAndReturnsRows() throws SQLException {
        String tablesSql = readSql(TABLES_RESOURCE);
        when(conn.prepareStatement(tablesSql)).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString(1)).thenReturn("orders", "customers");

        List<String> tables = provider.getTables(conn, "sales");

        assertEquals(List.of("orders", "customers"), tables);

        ArgumentCaptor<Integer> indexCaptor = ArgumentCaptor.forClass(Integer.class);
        ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
        verify(stmt).setObject(indexCaptor.capture(), valueCaptor.capture());
        assertEquals(1, indexCaptor.getValue());
        assertEquals("sales", valueCaptor.getValue());
    }

    @Test
    void getColumnsBindsSchemaAndTableToBothQueriesAndMapsEveryField() throws SQLException {
        String foreignKeysSql = readSql(FOREIGN_KEYS_RESOURCE);
        String columnsSql = readSql(COLUMNS_RESOURCE);
        PreparedStatement fkStmt = mock(PreparedStatement.class);
        PreparedStatement colStmt = mock(PreparedStatement.class);
        ResultSet fkRs = mock(ResultSet.class);
        ResultSet colRs = mock(ResultSet.class);

        when(conn.prepareStatement(foreignKeysSql)).thenReturn(fkStmt);
        when(conn.prepareStatement(columnsSql)).thenReturn(colStmt);
        when(fkStmt.executeQuery()).thenReturn(fkRs);
        when(colStmt.executeQuery()).thenReturn(colRs);

        // two FK rows, both on customer_id
        when(fkRs.next()).thenReturn(true, true, false);
        when(fkRs.getString("column_name")).thenReturn("customer_id", "customer_id");
        when(fkRs.getString("constraint_name")).thenReturn("fk_orders_customer", "fk_orders_client");
        when(fkRs.getString("referenced_schema")).thenReturn("sales", "crm");
        when(fkRs.getString("referenced_table")).thenReturn("customers", "clients");
        when(fkRs.getString("referenced_column")).thenReturn("id", "client_id");

        // three columns: pk, fk carrier, plain
        when(colRs.next()).thenReturn(true, true, true, false);
        when(colRs.getString("column_name")).thenReturn("id", "customer_id", "note");
        when(colRs.getString("data_type")).thenReturn("integer", "bigint", "text");
        when(colRs.getString("is_nullable")).thenReturn("NO", "YES", "yes");
        when(colRs.getInt("ordinal_position")).thenReturn(1, 2, 3);
        when(colRs.getString("column_default")).thenReturn("nextval('orders_id_seq')", null, null);
        when(colRs.getBoolean("is_primary_key")).thenReturn(true, false, false);

        List<DbColumnResponse> columns = provider.getColumns(conn, "sales", "orders");

        assertEquals(3, columns.size());

        DbColumnResponse id = columns.get(0);
        assertEquals("id", id.getName());
        assertEquals("integer", id.getDataType());
        assertFalse(id.isNullable());
        assertEquals(1, id.getOrdinalPosition());
        assertEquals("nextval('orders_id_seq')", id.getColumnDefault());
        assertTrue(id.isPrimaryKey());
        assertNotNull(id.getForeignKeys());
        assertTrue(id.getForeignKeys().isEmpty());

        DbColumnResponse customerId = columns.get(1);
        assertEquals("customer_id", customerId.getName());
        assertEquals("bigint", customerId.getDataType());
        assertTrue(customerId.isNullable());
        assertEquals(2, customerId.getOrdinalPosition());
        assertNull(customerId.getColumnDefault());
        assertFalse(customerId.isPrimaryKey());
        assertEquals(2, customerId.getForeignKeys().size());

        ForeignKeyReferenceDto first = customerId.getForeignKeys().get(0);
        assertEquals("fk_orders_customer", first.getConstraintName());
        assertEquals("sales", first.getReferencedSchema());
        assertEquals("customers", first.getReferencedTable());
        assertEquals("id", first.getReferencedColumn());

        ForeignKeyReferenceDto second = customerId.getForeignKeys().get(1);
        assertEquals("fk_orders_client", second.getConstraintName());
        assertEquals("crm", second.getReferencedSchema());
        assertEquals("clients", second.getReferencedTable());
        assertEquals("client_id", second.getReferencedColumn());

        // lowercase "yes" is not the exact marker "YES"
        DbColumnResponse note = columns.get(2);
        assertFalse(note.isNullable());
        assertNotNull(note.getForeignKeys());
        assertTrue(note.getForeignKeys().isEmpty());

        verify(fkStmt).setObject(1, "sales");
        verify(fkStmt).setObject(2, "orders");
        verify(colStmt).setObject(1, "sales");
        verify(colStmt).setObject(2, "orders");
    }

    @Test
    void getColumnsRunsForeignKeysQueryBeforeColumnsQuery() throws SQLException {
        String foreignKeysSql = readSql(FOREIGN_KEYS_RESOURCE);
        String columnsSql = readSql(COLUMNS_RESOURCE);
        PreparedStatement fkStmt = mock(PreparedStatement.class);
        PreparedStatement colStmt = mock(PreparedStatement.class);
        ResultSet fkRs = mock(ResultSet.class);
        ResultSet colRs = mock(ResultSet.class);

        when(conn.prepareStatement(foreignKeysSql)).thenReturn(fkStmt);
        when(conn.prepareStatement(columnsSql)).thenReturn(colStmt);
        when(fkStmt.executeQuery()).thenReturn(fkRs);
        when(colStmt.executeQuery()).thenReturn(colRs);
        when(fkRs.next()).thenReturn(false);
        when(colRs.next()).thenReturn(false);

        provider.getColumns(conn, "sales", "orders");

        InOrder inOrder = inOrder(conn, fkStmt, colStmt);
        inOrder.verify(conn).prepareStatement(foreignKeysSql);
        inOrder.verify(fkStmt).executeQuery();
        inOrder.verify(conn).prepareStatement(columnsSql);
        inOrder.verify(colStmt).executeQuery();
    }

    @Test
    void getSchemasClosesStatementAndResultSetButNeverTheConnection() throws SQLException {
        String schemasSql = readSql(SCHEMAS_RESOURCE);
        when(conn.prepareStatement(schemasSql)).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        provider.getSchemas(conn);

        verify(stmt).close();
        verify(rs).close();
        verify(conn, never()).close();
    }

    @Test
    void getTablesClosesStatementAndResultSetButNeverTheConnection() throws SQLException {
        String tablesSql = readSql(TABLES_RESOURCE);
        when(conn.prepareStatement(tablesSql)).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        provider.getTables(conn, "sales");

        verify(stmt).close();
        verify(rs).close();
        verify(conn, never()).close();
    }

    @Test
    void getColumnsClosesBothStatementsAndResultSetsButNeverTheConnection() throws SQLException {
        String foreignKeysSql = readSql(FOREIGN_KEYS_RESOURCE);
        String columnsSql = readSql(COLUMNS_RESOURCE);
        PreparedStatement fkStmt = mock(PreparedStatement.class);
        PreparedStatement colStmt = mock(PreparedStatement.class);
        ResultSet fkRs = mock(ResultSet.class);
        ResultSet colRs = mock(ResultSet.class);

        when(conn.prepareStatement(foreignKeysSql)).thenReturn(fkStmt);
        when(conn.prepareStatement(columnsSql)).thenReturn(colStmt);
        when(fkStmt.executeQuery()).thenReturn(fkRs);
        when(colStmt.executeQuery()).thenReturn(colRs);
        when(fkRs.next()).thenReturn(false);
        when(colRs.next()).thenReturn(false);

        provider.getColumns(conn, "sales", "orders");

        verify(fkStmt).close();
        verify(fkRs).close();
        verify(colStmt).close();
        verify(colRs).close();
        verify(conn, never()).close();
    }

    // --- helpers --------------------------------------------------------------------------------

    private static String readSql(String resourcePath) {
        try (InputStream in = AbstractDatabaseMetadataProviderTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(in, "missing classpath resource: " + resourcePath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Subclass used to drive the protected query helpers with hand-built queries.
     */
    private static final class TestProvider extends AbstractDatabaseMetadataProvider {

        private TestProvider() {
            super("postgresql");
        }

        @Override
        public DatabaseType getDatabaseType() {
            return DatabaseType.POSTGRESQL;
        }

        private List<String> runQueryStrings(Connection conn, MetadataQuery query, Object... params) throws SQLException {
            return queryStrings(conn, query, params);
        }
    }

    /**
     * Subclass pointing at a vendor directory that does not exist on the classpath.
     */
    private static final class BogusVendorProvider extends AbstractDatabaseMetadataProvider {

        private BogusVendorProvider() {
            super("bogus-vendor");
        }

        @Override
        public DatabaseType getDatabaseType() {
            return DatabaseType.POSTGRESQL;
        }
    }
}
