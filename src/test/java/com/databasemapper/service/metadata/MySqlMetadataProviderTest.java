package com.databasemapper.service.metadata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MySqlMetadataProvider#getSchemas(Connection)}, which answers from the
 * connection's own catalog instead of querying the server, plus a guard that the other vendors
 * still run their shipped SQL. All JDBC is mocked - no database is contacted.
 */
@ExtendWith(MockitoExtension.class)
class MySqlMetadataProviderTest {

    @Mock
    private Connection conn;

    @Mock
    private PreparedStatement stmt;

    @Mock
    private ResultSet rs;

    private final MySqlMetadataProvider provider = new MySqlMetadataProvider();

    // --- A. catalog shortcut ----------------------------------------------------------------------

    @Test
    void getSchemasReturnsOnlyTheConnectionCatalogAndRunsNoQuery() throws SQLException {
        when(conn.getCatalog()).thenReturn("petclinic");

        List<String> schemas = provider.getSchemas(conn);

        assertEquals(List.of("petclinic"), schemas);
        verify(conn, never()).prepareStatement(anyString());
    }

    // --- B. fallback to the shipped query ---------------------------------------------------------

    @Test
    void getSchemasFallsBackToTheShippedQueryWhenTheConnectionHasNoCatalog() throws SQLException {
        assertFallsBackToShippedQuery(null);
    }

    @Test
    void getSchemasFallsBackToTheShippedQueryWhenTheCatalogIsBlank() throws SQLException {
        assertFallsBackToShippedQuery("   ");
    }

    private void assertFallsBackToShippedQuery(String catalog) throws SQLException {
        String schemasSql = readSql("db/metadata/mysql/schemas.sql");
        when(conn.getCatalog()).thenReturn(catalog);
        when(conn.prepareStatement(schemasSql)).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString(1)).thenReturn("petclinic", "sakila");

        List<String> schemas = provider.getSchemas(conn);

        assertEquals(List.of("petclinic", "sakila"), schemas);
        verify(conn).prepareStatement(schemasSql);
        verify(stmt, never()).setObject(anyInt(), any());
    }

    // --- C. the other vendors are unaffected ------------------------------------------------------

    @Test
    void postgresProviderStillExecutesItsShippedSchemasQuery() throws SQLException {
        assertQueriesTheServer(new PostgresMetadataProvider(), "db/metadata/postgresql/schemas.sql");
    }

    @Test
    void oracleProviderStillExecutesItsShippedSchemasQuery() throws SQLException {
        assertQueriesTheServer(new OracleMetadataProvider(), "db/metadata/oracle/schemas.sql");
    }

    @Test
    void sqlServerProviderStillExecutesItsShippedSchemasQuery() throws SQLException {
        assertQueriesTheServer(new SqlServerMetadataProvider(), "db/metadata/sqlserver/schemas.sql");
    }

    /**
     * A catalog is offered but must be ignored: only MySQL short-circuits on it.
     */
    private void assertQueriesTheServer(AbstractDatabaseMetadataProvider provider, String resourcePath)
            throws SQLException {
        Connection otherConn = mock(Connection.class);
        PreparedStatement otherStmt = mock(PreparedStatement.class);
        ResultSet otherRs = mock(ResultSet.class);
        String schemasSql = readSql(resourcePath);

        when(otherConn.prepareStatement(schemasSql)).thenReturn(otherStmt);
        when(otherStmt.executeQuery()).thenReturn(otherRs);
        when(otherRs.next()).thenReturn(true, false);
        when(otherRs.getString(1)).thenReturn("public");

        assertEquals(List.of("public"), provider.getSchemas(otherConn));

        verify(otherConn).prepareStatement(schemasSql);
        verify(otherConn, never()).getCatalog();
    }

    // --- helpers ----------------------------------------------------------------------------------

    private static String readSql(String resourcePath) {
        try (InputStream in = MySqlMetadataProviderTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(in, "missing classpath resource: " + resourcePath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
