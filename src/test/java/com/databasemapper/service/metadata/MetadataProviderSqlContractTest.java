package com.databasemapper.service.metadata;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Guards the contract between each shipped {@code db/metadata/{vendor}/*.sql} file and the
 * Java code that binds its parameters: schemas.sql binds 0, tables.sql binds 1,
 * columns.sql and foreign-keys.sql bind 2. Constructing each provider also proves all
 * sixteen SQL resources are present on the classpath. All JDBC is mocked.
 */
@ExtendWith(MockitoExtension.class)
class MetadataProviderSqlContractTest {

    @Mock
    private Connection conn;

    @Mock
    private PreparedStatement stmt;

    @Mock
    private ResultSet rs;

    @Test
    void postgresqlProviderLoadsItsSqlAndItsPlaceholdersMatchTheJavaBindings() throws SQLException {
        assertVendorContract(assertDoesNotThrow(PostgresMetadataProvider::new), "postgresql");
    }

    @Test
    void mysqlProviderLoadsItsSqlAndItsPlaceholdersMatchTheJavaBindings() throws SQLException {
        assertVendorContract(assertDoesNotThrow(MySqlMetadataProvider::new), "mysql");
    }

    @Test
    void sqlServerProviderLoadsItsSqlAndItsPlaceholdersMatchTheJavaBindings() throws SQLException {
        assertVendorContract(assertDoesNotThrow(SqlServerMetadataProvider::new), "sqlserver");
    }

    @Test
    void oracleProviderLoadsItsSqlAndItsPlaceholdersMatchTheJavaBindings() throws SQLException {
        assertVendorContract(assertDoesNotThrow(OracleMetadataProvider::new), "oracle");
    }

    private void assertVendorContract(AbstractDatabaseMetadataProvider provider, String vendorDirectory) throws SQLException {
        assertPlaceholderCount(provider, vendorDirectory, "schemas.sql", 0);
        assertPlaceholderCount(provider, vendorDirectory, "tables.sql", 1);
        assertPlaceholderCount(provider, vendorDirectory, "columns.sql", 2);
        assertPlaceholderCount(provider, vendorDirectory, "foreign-keys.sql", 2);

        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        assertDoesNotThrow(() -> provider.getSchemas(conn),
                vendorDirectory + "/schemas.sql must bind exactly 0 parameters");
        assertDoesNotThrow(() -> provider.getTables(conn, "some_schema"),
                vendorDirectory + "/tables.sql must bind exactly 1 parameter");
        assertDoesNotThrow(() -> provider.getColumns(conn, "some_schema", "some_table"),
                vendorDirectory + "/{foreign-keys,columns}.sql must bind exactly 2 parameters");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(conn, times(4)).prepareStatement(sqlCaptor.capture());
        List<String> executed = sqlCaptor.getAllValues();
        assertEquals(readSql(resourcePath(vendorDirectory, "schemas.sql")), executed.get(0));
        assertEquals(readSql(resourcePath(vendorDirectory, "tables.sql")), executed.get(1));
        assertEquals(readSql(resourcePath(vendorDirectory, "foreign-keys.sql")), executed.get(2));
        assertEquals(readSql(resourcePath(vendorDirectory, "columns.sql")), executed.get(3));
    }

    /**
     * Binds one argument too many so the guard reports the real placeholder count of the shipped file.
     */
    private void assertPlaceholderCount(AbstractDatabaseMetadataProvider provider,
                                        String vendorDirectory,
                                        String fileName,
                                        int expectedPlaceholders) {
        String resourcePath = resourcePath(vendorDirectory, fileName);
        AbstractDatabaseMetadataProvider.MetadataQuery query =
                new AbstractDatabaseMetadataProvider.MetadataQuery(resourcePath, readSql(resourcePath));
        Object[] oneTooMany = new Object[expectedPlaceholders + 1];

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provider.queryStrings(conn, query, oneTooMany));

        assertTrue(ex.getMessage().contains("declares %d parameter placeholder(s)".formatted(expectedPlaceholders)),
                "%s must declare exactly %d placeholder(s) but the guard reported: %s"
                        .formatted(resourcePath, expectedPlaceholders, ex.getMessage()));
    }

    private static String resourcePath(String vendorDirectory, String fileName) {
        return "db/metadata/%s/%s".formatted(vendorDirectory, fileName);
    }

    private static String readSql(String resourcePath) {
        try (InputStream in = MetadataProviderSqlContractTest.class.getClassLoader().getResourceAsStream(resourcePath)) {
            assertNotNull(in, "missing classpath resource: " + resourcePath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
