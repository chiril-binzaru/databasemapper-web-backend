package com.databasemapper.service;

import com.databasemapper.config.JdbcConnectionFactory;
import com.databasemapper.dto.ConnectionRequest;
import com.databasemapper.dto.ConnectionResponse;
import com.databasemapper.model.Connection;
import com.databasemapper.model.Database;
import com.databasemapper.model.enums.ConnectionMode;
import com.databasemapper.model.enums.DatabaseType;
import com.databasemapper.repository.ConnectionRepository;
import com.databasemapper.repository.DatabaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConnectionServiceImpl}, focused on the vendor guard applied to
 * connection strings and on which fields {@code update} writes in each connection mode.
 * All collaborators are mocked - no database and no JDBC driver are involved.
 */
@ExtendWith(MockitoExtension.class)
class ConnectionServiceImplTest {

    private static final Long DATABASE_ID = 7L;
    private static final Long CONNECTION_ID = 3L;

    /**
     * Carries both a host and a password after the scheme, so a leaky error message is detectable.
     */
    private static final String ORACLE_CONNECTION_STRING = "jdbc:oracle:thin:@//secret-host:1521/db?password=hunter2";

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private DatabaseRepository databaseRepository;

    @Mock
    private JdbcConnectionFactory jdbcConnectionFactory;

    @InjectMocks
    private ConnectionServiceImpl connectionService;

    @Captor
    private ArgumentCaptor<Connection> connectionCaptor;

    // --- A. vendor guard: rejection ---------------------------------------------------------------

    @Test
    void createRejectsConnectionStringBelongingToAnotherVendorWithBadRequestAndSavesNothing() {
        stubDatabase(DatabaseType.POSTGRESQL);
        when(jdbcConnectionFactory.expectedUrlPrefix(DatabaseType.POSTGRESQL)).thenReturn("jdbc:postgresql:");
        ConnectionRequest request = connectionStringRequest(ORACLE_CONNECTION_STRING);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> connectionService.create(DATABASE_ID, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(connectionRepository, never()).save(any());
    }

    @Test
    void updateRejectsConnectionStringBelongingToAnotherVendorWithBadRequestAndSavesNothing() {
        stubDatabase(DatabaseType.POSTGRESQL);
        when(jdbcConnectionFactory.expectedUrlPrefix(DatabaseType.POSTGRESQL)).thenReturn("jdbc:postgresql:");
        ConnectionRequest request = connectionStringRequest(ORACLE_CONNECTION_STRING);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> connectionService.update(DATABASE_ID, CONNECTION_ID, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(connectionRepository, never()).save(any());
        // the guard runs before the connection is even loaded
        verify(connectionRepository, never()).findByConnectionIdAndDatabase_DatabaseId(any(), any());
    }

    @Test
    void testConnectionFromRequestRejectsConnectionStringBelongingToAnotherVendorAndOpensNoConnection()
            throws SQLException {
        stubDatabase(DatabaseType.POSTGRESQL);
        when(jdbcConnectionFactory.expectedUrlPrefix(DatabaseType.POSTGRESQL)).thenReturn("jdbc:postgresql:");
        ConnectionRequest request = connectionStringRequest(ORACLE_CONNECTION_STRING);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> connectionService.testConnectionFromRequest(DATABASE_ID, request));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(jdbcConnectionFactory, never()).openConnection(any(), any());
        verify(connectionRepository, never()).save(any());
    }

    @Test
    void rejectionMessageNamesBothSchemesButLeaksNothingElseFromTheConnectionString() {
        stubDatabase(DatabaseType.POSTGRESQL);
        when(jdbcConnectionFactory.expectedUrlPrefix(DatabaseType.POSTGRESQL)).thenReturn("jdbc:postgresql:");
        ConnectionRequest request = connectionStringRequest(ORACLE_CONNECTION_STRING);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> connectionService.create(DATABASE_ID, request));

        String reason = ex.getReason();
        assertNotNull(reason);
        assertTrue(reason.contains("jdbc:postgresql:"),
                "message should name the expected prefix but was: " + reason);
        assertTrue(reason.contains("jdbc:oracle:"),
                "message should name the offending scheme but was: " + reason);
        assertFalse(reason.contains("hunter2"),
                "message must not leak credentials from the connection string but was: " + reason);
        assertFalse(reason.contains("secret-host"),
                "message must not leak the host from the connection string but was: " + reason);
        assertFalse(ex.getMessage().contains("hunter2"),
                "exception message must not leak credentials but was: " + ex.getMessage());
        assertFalse(ex.getMessage().contains("secret-host"),
                "exception message must not leak the host but was: " + ex.getMessage());
    }

    @Test
    void rejectionMessageLeaksNothingWhenTheStringHasNoRecognisableJdbcScheme() {
        // A string without a jdbc: scheme still has colons, and its first ones can sit after a host or
        // a username. Only a real jdbc:vendor: scheme may be echoed back.
        String[] schemeless = {
                "//secret-host:1521/db?password=hunter2",
                "user:hunter2@secret-host",
                "postgresql://admin:hunter2@secret-host/db"
        };

        for (String connectionString : schemeless) {
            stubDatabase(DatabaseType.POSTGRESQL);
            when(jdbcConnectionFactory.expectedUrlPrefix(DatabaseType.POSTGRESQL)).thenReturn("jdbc:postgresql:");

            ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                    () -> connectionService.create(DATABASE_ID, connectionStringRequest(connectionString)));

            String reason = ex.getReason();
            assertNotNull(reason);
            assertTrue(reason.contains("an unrecognised scheme"),
                    "schemeless input should be described generically but was: " + reason);
            assertFalse(reason.contains("hunter2"),
                    "message must not leak credentials but was: " + reason);
            assertFalse(reason.contains("secret-host"),
                    "message must not leak the host but was: " + reason);
            assertFalse(reason.contains("admin"),
                    "message must not leak the username but was: " + reason);
        }

        verify(connectionRepository, never()).save(any());
    }

    // --- B. vendor guard: acceptance --------------------------------------------------------------

    @Test
    void createAcceptsConnectionStringMatchingTheVendorPrefixAndSavesIt() {
        Database database = stubDatabase(DatabaseType.POSTGRESQL);
        when(jdbcConnectionFactory.expectedUrlPrefix(DatabaseType.POSTGRESQL)).thenReturn("jdbc:postgresql:");
        stubSaveReturningArgument();
        ConnectionRequest request = connectionStringRequest("jdbc:postgresql://db-host:5432/appdb");

        ConnectionResponse response = connectionService.create(DATABASE_ID, request);

        verify(connectionRepository).save(connectionCaptor.capture());
        Connection saved = connectionCaptor.getValue();
        assertEquals(database, saved.getDatabase());
        assertEquals("jdbc:postgresql://db-host:5432/appdb", saved.getConnectionString());
        assertEquals(ConnectionMode.CONNECTION_STRING, saved.getConnectionMode());
        assertEquals("jdbc:postgresql://db-host:5432/appdb", response.getConnectionString());
    }

    @Test
    void createAcceptsConnectionStringWhoseSchemeDiffersOnlyInCase() {
        stubDatabase(DatabaseType.POSTGRESQL);
        when(jdbcConnectionFactory.expectedUrlPrefix(DatabaseType.POSTGRESQL)).thenReturn("jdbc:postgresql:");
        stubSaveReturningArgument();
        ConnectionRequest request = connectionStringRequest("JDBC:POSTGRESQL://db-host:5432/appdb");

        ConnectionResponse response = connectionService.create(DATABASE_ID, request);

        verify(connectionRepository).save(connectionCaptor.capture());
        assertEquals("JDBC:POSTGRESQL://db-host:5432/appdb", connectionCaptor.getValue().getConnectionString());
        assertEquals("JDBC:POSTGRESQL://db-host:5432/appdb", response.getConnectionString());
    }

    @Test
    void createSkipsTheGuardEntirelyInParametersModeEvenWithANonsenseConnectionString() {
        stubDatabase(DatabaseType.POSTGRESQL);
        stubSaveReturningArgument();
        ConnectionRequest request = new ConnectionRequest();
        request.setConnectionMode(ConnectionMode.PARAMETERS);
        request.setPort(5432);
        request.setUsername("app_user");
        request.setPassword("s3cret");
        request.setConnectionString("not-even-a-url");

        connectionService.create(DATABASE_ID, request);

        verify(connectionRepository).save(any());
        verify(jdbcConnectionFactory, never()).expectedUrlPrefix(any());
    }

    @Test
    void createSkipsTheGuardWhenTheConnectionStringIsBlank() {
        stubDatabase(DatabaseType.POSTGRESQL);
        stubSaveReturningArgument();
        ConnectionRequest request = connectionStringRequest("   ");

        connectionService.create(DATABASE_ID, request);

        verify(connectionRepository).save(any());
        verify(jdbcConnectionFactory, never()).expectedUrlPrefix(any());
    }

    @Test
    void testConnectionFromRequestOpensAConnectionWhenThePrefixMatches() throws SQLException {
        Database database = stubDatabase(DatabaseType.ORACLE);
        when(jdbcConnectionFactory.expectedUrlPrefix(DatabaseType.ORACLE)).thenReturn("jdbc:oracle:");
        when(jdbcConnectionFactory.openConnection(any(), any())).thenReturn(mock(java.sql.Connection.class));
        ConnectionRequest request = connectionStringRequest(ORACLE_CONNECTION_STRING);

        connectionService.testConnectionFromRequest(DATABASE_ID, request);

        ArgumentCaptor<Database> databaseCaptor = ArgumentCaptor.forClass(Database.class);
        verify(jdbcConnectionFactory).openConnection(databaseCaptor.capture(), connectionCaptor.capture());
        assertEquals(database, databaseCaptor.getValue());
        assertEquals(ORACLE_CONNECTION_STRING, connectionCaptor.getValue().getConnectionString());
    }

    @Test
    void updateAcceptsConnectionStringMatchingTheVendorPrefixAndSavesIt() {
        stubDatabase(DatabaseType.MYSQL);
        when(jdbcConnectionFactory.expectedUrlPrefix(DatabaseType.MYSQL)).thenReturn("jdbc:mysql:");
        stubExistingConnection();
        stubSaveReturningArgument();
        ConnectionRequest request = connectionStringRequest("jdbc:mysql://db-host:3306/appdb");

        connectionService.update(DATABASE_ID, CONNECTION_ID, request);

        verify(connectionRepository).save(connectionCaptor.capture());
        assertEquals("jdbc:mysql://db-host:3306/appdb", connectionCaptor.getValue().getConnectionString());
    }

    // --- C. update field handling per mode --------------------------------------------------------

    @Test
    void updateInConnectionStringModePreservesUsernameAndPasswordAndClearsThePort() {
        stubDatabase(DatabaseType.POSTGRESQL);
        when(jdbcConnectionFactory.expectedUrlPrefix(DatabaseType.POSTGRESQL)).thenReturn("jdbc:postgresql:");
        Connection existing = stubExistingConnection();
        existing.setPort(5432);
        existing.setUsername("old_user");
        existing.setPassword("old_secret");
        stubSaveReturningArgument();

        ConnectionRequest request = connectionStringRequest("jdbc:postgresql://db-host:5432/appdb");
        request.setUsername("app_user");
        request.setPassword("s3cret");

        ConnectionResponse response = connectionService.update(DATABASE_ID, CONNECTION_ID, request);

        verify(connectionRepository).save(connectionCaptor.capture());
        Connection saved = connectionCaptor.getValue();
        assertEquals(ConnectionMode.CONNECTION_STRING, saved.getConnectionMode());
        assertEquals("jdbc:postgresql://db-host:5432/appdb", saved.getConnectionString());
        assertNull(saved.getPort());
        assertEquals("app_user", saved.getUsername());
        assertEquals("s3cret", saved.getPassword());

        assertEquals("app_user", response.getUsername());
        assertNull(response.getPort());
    }

    @Test
    void updateInConnectionStringModeWritesNullCredentialsThroughWhenTheRequestOmitsThem() {
        stubDatabase(DatabaseType.POSTGRESQL);
        when(jdbcConnectionFactory.expectedUrlPrefix(DatabaseType.POSTGRESQL)).thenReturn("jdbc:postgresql:");
        Connection existing = stubExistingConnection();
        existing.setUsername("old_user");
        existing.setPassword("old_secret");
        stubSaveReturningArgument();

        ConnectionRequest request = connectionStringRequest("jdbc:postgresql://db-host:5432/appdb");

        connectionService.update(DATABASE_ID, CONNECTION_ID, request);

        verify(connectionRepository).save(connectionCaptor.capture());
        Connection saved = connectionCaptor.getValue();
        assertNull(saved.getUsername());
        assertNull(saved.getPassword());
    }

    @Test
    void updateInParametersModeClearsTheConnectionStringAndWritesTheParameters() {
        stubDatabase(DatabaseType.POSTGRESQL);
        Connection existing = stubExistingConnection();
        existing.setConnectionMode(ConnectionMode.CONNECTION_STRING);
        existing.setConnectionString("jdbc:postgresql://db-host:5432/appdb");
        stubSaveReturningArgument();

        ConnectionRequest request = new ConnectionRequest();
        request.setConnectionMode(ConnectionMode.PARAMETERS);
        request.setPort(5432);
        request.setUsername("app_user");
        request.setPassword("s3cret");

        connectionService.update(DATABASE_ID, CONNECTION_ID, request);

        verify(connectionRepository).save(connectionCaptor.capture());
        Connection saved = connectionCaptor.getValue();
        assertEquals(ConnectionMode.PARAMETERS, saved.getConnectionMode());
        assertNull(saved.getConnectionString());
        assertEquals(5432, saved.getPort());
        assertEquals("app_user", saved.getUsername());
        assertEquals("s3cret", saved.getPassword());
        verify(jdbcConnectionFactory, never()).expectedUrlPrefix(any());
    }

    // --- helpers ----------------------------------------------------------------------------------

    private Database stubDatabase(DatabaseType databaseType) {
        Database database = new Database();
        database.setDatabaseId(DATABASE_ID);
        database.setDatabaseType(databaseType);
        database.setDatabaseHost("db-host");
        database.setDatabaseName("appdb");
        when(databaseRepository.findById(DATABASE_ID)).thenReturn(Optional.of(database));
        return database;
    }

    private Connection stubExistingConnection() {
        Database database = new Database();
        database.setDatabaseId(DATABASE_ID);
        Connection connection = new Connection();
        connection.setConnectionId(CONNECTION_ID);
        connection.setDatabase(database);
        connection.setConnectionMode(ConnectionMode.PARAMETERS);
        when(connectionRepository.findByConnectionIdAndDatabase_DatabaseId(CONNECTION_ID, DATABASE_ID))
                .thenReturn(Optional.of(connection));
        return connection;
    }

    private void stubSaveReturningArgument() {
        when(connectionRepository.save(any(Connection.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static ConnectionRequest connectionStringRequest(String connectionString) {
        ConnectionRequest request = new ConnectionRequest();
        request.setConnectionMode(ConnectionMode.CONNECTION_STRING);
        request.setConnectionString(connectionString);
        return request;
    }
}
