package com.databasemapper.config;

import com.databasemapper.model.Connection;
import com.databasemapper.model.Database;
import com.databasemapper.model.enums.ConnectionMode;
import com.databasemapper.model.enums.DatabaseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.DriverManager;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

/**
 * Unit tests for {@link JdbcConnectionFactory}. The factory is constructed directly with the two
 * SQL Server flags that Spring would otherwise inject via {@code @Value}. No driver is loaded and
 * no database is contacted: {@link DriverManager} is mocked statically.
 */
@ExtendWith(MockitoExtension.class)
class JdbcConnectionFactoryTest {

    private static final String CONNECTION_STRING = "jdbc:postgresql://db-host:5432/appdb";

    // --- A. buildJdbcUrl: the four vendor URL formats --------------------------------------------

    @Test
    void buildJdbcUrlRendersThePostgresqlFormat() {
        JdbcConnectionFactory factory = new JdbcConnectionFactory(false, true);

        assertEquals("jdbc:postgresql://db-host:5432/appdb",
                factory.buildJdbcUrl(database(DatabaseType.POSTGRESQL), parametersConnection(5432)));
    }

    @Test
    void buildJdbcUrlRendersTheMysqlFormat() {
        JdbcConnectionFactory factory = new JdbcConnectionFactory(false, true);

        assertEquals("jdbc:mysql://db-host:3306/appdb",
                factory.buildJdbcUrl(database(DatabaseType.MYSQL), parametersConnection(3306)));
    }

    @Test
    void buildJdbcUrlRendersTheOracleThinFormat() {
        JdbcConnectionFactory factory = new JdbcConnectionFactory(false, true);

        assertEquals("jdbc:oracle:thin:@//db-host:1521/appdb",
                factory.buildJdbcUrl(database(DatabaseType.ORACLE), parametersConnection(1521)));
    }

    @Test
    void buildJdbcUrlRendersSqlServerWithEncryptFalseAndTrustServerCertificateTrue() {
        JdbcConnectionFactory factory = new JdbcConnectionFactory(false, true);

        assertEquals("jdbc:sqlserver://db-host:1433;databaseName=appdb;encrypt=false;trustServerCertificate=true",
                factory.buildJdbcUrl(database(DatabaseType.SQL_SERVER), parametersConnection(1433)));
    }

    @Test
    void buildJdbcUrlRendersSqlServerWithEncryptTrueAndTrustServerCertificateTrue() {
        JdbcConnectionFactory factory = new JdbcConnectionFactory(true, true);

        assertEquals("jdbc:sqlserver://db-host:1433;databaseName=appdb;encrypt=true;trustServerCertificate=true",
                factory.buildJdbcUrl(database(DatabaseType.SQL_SERVER), parametersConnection(1433)));
    }

    @Test
    void buildJdbcUrlRendersSqlServerWithTrustServerCertificateFalse() {
        JdbcConnectionFactory factory = new JdbcConnectionFactory(true, false);

        assertEquals("jdbc:sqlserver://db-host:1433;databaseName=appdb;encrypt=true;trustServerCertificate=false",
                factory.buildJdbcUrl(database(DatabaseType.SQL_SERVER), parametersConnection(1433)));
    }

    @Test
    void buildJdbcUrlIgnoresTheSqlServerFlagsForTheOtherVendors() {
        JdbcConnectionFactory encrypting = new JdbcConnectionFactory(true, false);

        assertEquals("jdbc:postgresql://db-host:5432/appdb",
                encrypting.buildJdbcUrl(database(DatabaseType.POSTGRESQL), parametersConnection(5432)));
        assertEquals("jdbc:mysql://db-host:3306/appdb",
                encrypting.buildJdbcUrl(database(DatabaseType.MYSQL), parametersConnection(3306)));
        assertEquals("jdbc:oracle:thin:@//db-host:1521/appdb",
                encrypting.buildJdbcUrl(database(DatabaseType.ORACLE), parametersConnection(1521)));
    }

    // --- B. expectedUrlPrefix -------------------------------------------------------------------

    @Test
    void expectedUrlPrefixReturnsTheSchemeForEveryDatabaseType() {
        JdbcConnectionFactory factory = new JdbcConnectionFactory(false, true);

        assertEquals("jdbc:postgresql:", factory.expectedUrlPrefix(DatabaseType.POSTGRESQL));
        assertEquals("jdbc:mysql:", factory.expectedUrlPrefix(DatabaseType.MYSQL));
        assertEquals("jdbc:oracle:", factory.expectedUrlPrefix(DatabaseType.ORACLE));
        assertEquals("jdbc:sqlserver:", factory.expectedUrlPrefix(DatabaseType.SQL_SERVER));
    }

    @Test
    void expectedUrlPrefixIsAPrefixOfTheUrlBuiltForTheSameVendor() {
        JdbcConnectionFactory factory = new JdbcConnectionFactory(false, true);

        for (DatabaseType type : DatabaseType.values()) {
            String url = factory.buildJdbcUrl(database(type), parametersConnection(1234));
            assertEquals(factory.expectedUrlPrefix(type),
                    url.substring(0, factory.expectedUrlPrefix(type).length()),
                    "built URL for " + type + " must start with its expected prefix but was: " + url);
        }
    }

    // --- C. openConnection routing --------------------------------------------------------------

    @Test
    void openConnectionInConnectionStringModeWithoutUsernamePassesOnlyTheUrl() throws SQLException {
        JdbcConnectionFactory factory = new JdbcConnectionFactory(false, true);
        Connection connection = connectionStringConnection(null, null);
        java.sql.Connection opened = mock(java.sql.Connection.class);

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager.when(() -> DriverManager.getConnection(CONNECTION_STRING)).thenReturn(opened);

            assertSame(opened, factory.openConnection(database(DatabaseType.POSTGRESQL), connection));

            driverManager.verify(() -> DriverManager.getConnection(CONNECTION_STRING));
            driverManager.verify(() -> DriverManager.getConnection(anyString(), anyString(), anyString()), never());
        }
    }

    @Test
    void openConnectionInConnectionStringModeWithBlankUsernamePassesOnlyTheUrl() throws SQLException {
        JdbcConnectionFactory factory = new JdbcConnectionFactory(false, true);
        Connection connection = connectionStringConnection("  ", "s3cret");
        java.sql.Connection opened = mock(java.sql.Connection.class);

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager.when(() -> DriverManager.getConnection(CONNECTION_STRING)).thenReturn(opened);

            assertSame(opened, factory.openConnection(database(DatabaseType.POSTGRESQL), connection));

            driverManager.verify(() -> DriverManager.getConnection(CONNECTION_STRING));
            driverManager.verify(() -> DriverManager.getConnection(anyString(), anyString(), anyString()), never());
        }
    }

    @Test
    void openConnectionInConnectionStringModeWithUsernamePassesCredentialsAlongsideTheUrl() throws SQLException {
        JdbcConnectionFactory factory = new JdbcConnectionFactory(false, true);
        Connection connection = connectionStringConnection("app_user", "s3cret");
        java.sql.Connection opened = mock(java.sql.Connection.class);

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager.when(() -> DriverManager.getConnection(CONNECTION_STRING, "app_user", "s3cret"))
                    .thenReturn(opened);

            assertSame(opened, factory.openConnection(database(DatabaseType.POSTGRESQL), connection));

            driverManager.verify(() -> DriverManager.getConnection(CONNECTION_STRING, "app_user", "s3cret"));
            driverManager.verify(() -> DriverManager.getConnection(anyString()), never());
        }
    }

    @Test
    void openConnectionInParametersModePassesTheBuiltUrlWithCredentials() throws SQLException {
        JdbcConnectionFactory factory = new JdbcConnectionFactory(false, true);
        Connection connection = parametersConnection(1433);
        connection.setUsername("sa");
        connection.setPassword("s3cret");
        java.sql.Connection opened = mock(java.sql.Connection.class);
        String expectedUrl =
                "jdbc:sqlserver://db-host:1433;databaseName=appdb;encrypt=false;trustServerCertificate=true";

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            driverManager.when(() -> DriverManager.getConnection(expectedUrl, "sa", "s3cret")).thenReturn(opened);

            assertSame(opened, factory.openConnection(database(DatabaseType.SQL_SERVER), connection));

            driverManager.verify(() -> DriverManager.getConnection(expectedUrl, "sa", "s3cret"));
            driverManager.verify(() -> DriverManager.getConnection(anyString()), never());
        }
    }

    @Test
    void openConnectionInParametersModeIgnoresAnyStoredConnectionString() throws SQLException {
        JdbcConnectionFactory factory = new JdbcConnectionFactory(false, true);
        Connection connection = parametersConnection(5432);
        connection.setUsername("app_user");
        connection.setPassword("s3cret");
        connection.setConnectionString("jdbc:mysql://other-host:3306/otherdb");

        try (MockedStatic<DriverManager> driverManager = mockStatic(DriverManager.class)) {
            factory.openConnection(database(DatabaseType.POSTGRESQL), connection);

            driverManager.verify(() ->
                    DriverManager.getConnection("jdbc:postgresql://db-host:5432/appdb", "app_user", "s3cret"));
        }
    }

    // --- helpers --------------------------------------------------------------------------------

    private static Database database(DatabaseType databaseType) {
        Database database = new Database();
        database.setDatabaseId(7L);
        database.setDatabaseType(databaseType);
        database.setDatabaseHost("db-host");
        database.setDatabaseName("appdb");
        return database;
    }

    private static Connection parametersConnection(int port) {
        Connection connection = new Connection();
        connection.setConnectionId(1L);
        connection.setConnectionMode(ConnectionMode.PARAMETERS);
        connection.setPort(port);
        return connection;
    }

    private static Connection connectionStringConnection(String username, String password) {
        Connection connection = new Connection();
        connection.setConnectionId(1L);
        connection.setConnectionMode(ConnectionMode.CONNECTION_STRING);
        connection.setConnectionString(CONNECTION_STRING);
        connection.setUsername(username);
        connection.setPassword(password);
        return connection;
    }
}
