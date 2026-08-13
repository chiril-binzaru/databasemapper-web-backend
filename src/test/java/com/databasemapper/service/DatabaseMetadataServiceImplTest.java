package com.databasemapper.service;

import com.databasemapper.config.JdbcConnectionFactory;
import com.databasemapper.dto.DbColumnResponse;
import com.databasemapper.model.Connection;
import com.databasemapper.model.Database;
import com.databasemapper.model.enums.DatabaseType;
import com.databasemapper.repository.ConnectionRepository;
import com.databasemapper.repository.DatabaseRepository;
import com.databasemapper.service.metadata.AbstractDatabaseMetadataProvider;
import com.databasemapper.service.metadata.MySqlMetadataProvider;
import com.databasemapper.service.metadata.OracleMetadataProvider;
import com.databasemapper.service.metadata.PostgresMetadataProvider;
import com.databasemapper.service.metadata.SqlServerMetadataProvider;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseMetadataServiceImplTest {

    private static final Long DATABASE_ID = 7L;

    @Mock
    private DatabaseRepository databaseRepository;

    @Mock
    private ConnectionRepository connectionRepository;

    @Mock
    private JdbcConnectionFactory jdbcConnectionFactory;

    @Mock
    private PostgresMetadataProvider postgresProvider;

    @Mock
    private MySqlMetadataProvider mySqlProvider;

    @Mock
    private OracleMetadataProvider oracleProvider;

    @Mock
    private SqlServerMetadataProvider sqlServerProvider;

    @Mock
    private java.sql.Connection jdbcConnection;

    // --- registry -------------------------------------------------------------------------------

    @Test
    void constructorThrowsWhenTwoProvidersClaimTheSameDatabaseType() {
        lenient().when(mySqlProvider.getDatabaseType()).thenReturn(DatabaseType.MYSQL);
        lenient().when(postgresProvider.getDatabaseType()).thenReturn(DatabaseType.MYSQL);
        List<AbstractDatabaseMetadataProvider> duplicates = List.of(mySqlProvider, postgresProvider);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new DatabaseMetadataServiceImpl(databaseRepository, connectionRepository, jdbcConnectionFactory, duplicates));

        assertTrue(ex.getMessage().contains("MYSQL"), "message should name the type but was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("MySqlMetadataProvider"),
                "message should name the first provider but was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("PostgresMetadataProvider"),
                "message should name the second provider but was: " + ex.getMessage());
    }

    @Test
    void constructorThrowsWhenADatabaseTypeHasNoProvider() {
        lenient().when(postgresProvider.getDatabaseType()).thenReturn(DatabaseType.POSTGRESQL);
        lenient().when(mySqlProvider.getDatabaseType()).thenReturn(DatabaseType.MYSQL);
        List<AbstractDatabaseMetadataProvider> incomplete = List.of(postgresProvider, mySqlProvider);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new DatabaseMetadataServiceImpl(databaseRepository, connectionRepository, jdbcConnectionFactory, incomplete));

        assertTrue(ex.getMessage().contains("ORACLE"), "message should list ORACLE but was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("SQL_SERVER"), "message should list SQL_SERVER but was: " + ex.getMessage());
    }

    @Test
    void constructorSucceedsWithExactlyOneProviderPerDatabaseType() {
        DatabaseMetadataServiceImpl service = assertDoesNotThrow(this::newService);

        assertNotNull(service);
    }

    // --- dispatch -------------------------------------------------------------------------------

    @Test
    void getSchemasDispatchesToThePostgresProviderOnly() throws SQLException {
        assertDispatchesTo(DatabaseType.POSTGRESQL, postgresProvider);
    }

    @Test
    void getSchemasDispatchesToTheMySqlProviderOnly() throws SQLException {
        assertDispatchesTo(DatabaseType.MYSQL, mySqlProvider);
    }

    @Test
    void getSchemasDispatchesToTheOracleProviderOnly() throws SQLException {
        assertDispatchesTo(DatabaseType.ORACLE, oracleProvider);
    }

    @Test
    void getSchemasDispatchesToTheSqlServerProviderOnly() throws SQLException {
        assertDispatchesTo(DatabaseType.SQL_SERVER, sqlServerProvider);
    }

    @Test
    void getTablesDelegatesToTheProviderForTheDatabaseTypeAndReturnsItsResult() throws SQLException {
        DatabaseMetadataServiceImpl service = newService();
        stubDatabaseAndConnection(DatabaseType.MYSQL);
        when(mySqlProvider.getTables(jdbcConnection, "sales")).thenReturn(List.of("orders"));

        assertEquals(List.of("orders"), service.getTables(DATABASE_ID, "sales"));
        verify(postgresProvider, never()).getTables(any(), anyString());
        verify(oracleProvider, never()).getTables(any(), anyString());
        verify(sqlServerProvider, never()).getTables(any(), anyString());
    }

    @Test
    void getColumnsDelegatesToTheProviderForTheDatabaseTypeAndReturnsItsResult() throws SQLException {
        DatabaseMetadataServiceImpl service = newService();
        stubDatabaseAndConnection(DatabaseType.ORACLE);
        List<DbColumnResponse> columns = List.of(DbColumnResponse.builder().name("ID").build());
        when(oracleProvider.getColumns(jdbcConnection, "HR", "EMPLOYEES")).thenReturn(columns);

        assertEquals(columns, service.getColumns(DATABASE_ID, "HR", "EMPLOYEES"));
        verify(postgresProvider, never()).getColumns(any(), anyString(), anyString());
        verify(mySqlProvider, never()).getColumns(any(), anyString(), anyString());
        verify(sqlServerProvider, never()).getColumns(any(), anyString(), anyString());
    }

    // --- lookup failures ------------------------------------------------------------------------

    @Test
    void getSchemasThrowsEntityNotFoundAndOpensNoConnectionWhenDatabaseIsMissing() throws SQLException {
        DatabaseMetadataServiceImpl service = newService();
        when(databaseRepository.findById(DATABASE_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.getSchemas(DATABASE_ID));
        verify(jdbcConnectionFactory, never()).openConnection(any(), any());
    }

    @Test
    void getSchemasThrowsUnprocessableEntityAndOpensNoConnectionWhenNoActiveConnectionExists() throws SQLException {
        DatabaseMetadataServiceImpl service = newService();
        when(databaseRepository.findById(DATABASE_ID)).thenReturn(Optional.of(database(DatabaseType.POSTGRESQL)));
        when(connectionRepository.findActiveByDatabaseId(DATABASE_ID)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getSchemas(DATABASE_ID));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertTrue(ex.getReason().contains("No active connection found for database: " + DATABASE_ID),
                "unexpected reason: " + ex.getReason());
        verify(jdbcConnectionFactory, never()).openConnection(any(), any());
    }

    // --- SQLException translation ---------------------------------------------------------------

    @Test
    void getSchemasTranslatesSqlExceptionToUnprocessableEntity() throws SQLException {
        DatabaseMetadataServiceImpl service = newService();
        stubDatabaseAndConnection(DatabaseType.POSTGRESQL);
        when(postgresProvider.getSchemas(jdbcConnection)).thenThrow(new SQLException("connection reset"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getSchemas(DATABASE_ID));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Failed to fetch schemas from database: connection reset"),
                "unexpected reason: " + ex.getReason());
    }

    @Test
    void getTablesTranslatesSqlExceptionToUnprocessableEntity() throws SQLException {
        DatabaseMetadataServiceImpl service = newService();
        stubDatabaseAndConnection(DatabaseType.POSTGRESQL);
        when(postgresProvider.getTables(jdbcConnection, "sales")).thenThrow(new SQLException("connection reset"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getTables(DATABASE_ID, "sales"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Failed to fetch tables from database: connection reset"),
                "unexpected reason: " + ex.getReason());
    }

    @Test
    void getColumnsTranslatesSqlExceptionToUnprocessableEntity() throws SQLException {
        DatabaseMetadataServiceImpl service = newService();
        stubDatabaseAndConnection(DatabaseType.POSTGRESQL);
        when(postgresProvider.getColumns(jdbcConnection, "sales", "orders"))
                .thenThrow(new SQLException("connection reset"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getColumns(DATABASE_ID, "sales", "orders"));

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Failed to fetch columns from database: connection reset"),
                "unexpected reason: " + ex.getReason());
    }

    // --- connection lifecycle -------------------------------------------------------------------

    @Test
    void getSchemasClosesTheJdbcConnectionItOpened() throws SQLException {
        DatabaseMetadataServiceImpl service = newService();
        stubDatabaseAndConnection(DatabaseType.POSTGRESQL);
        when(postgresProvider.getSchemas(jdbcConnection)).thenReturn(List.of("public"));

        service.getSchemas(DATABASE_ID);

        verify(jdbcConnection).close();
    }

    @Test
    void getSchemasClosesTheJdbcConnectionWhenTheProviderFails() throws SQLException {
        DatabaseMetadataServiceImpl service = newService();
        stubDatabaseAndConnection(DatabaseType.POSTGRESQL);
        when(postgresProvider.getSchemas(jdbcConnection)).thenThrow(new SQLException("connection reset"));

        assertThrows(ResponseStatusException.class, () -> service.getSchemas(DATABASE_ID));

        verify(jdbcConnection).close();
    }

    // --- helpers --------------------------------------------------------------------------------

    private void assertDispatchesTo(DatabaseType databaseType, AbstractDatabaseMetadataProvider expected) throws SQLException {
        DatabaseMetadataServiceImpl service = newService();
        stubDatabaseAndConnection(databaseType);
        when(expected.getSchemas(jdbcConnection)).thenReturn(List.of("public"));

        assertEquals(List.of("public"), service.getSchemas(DATABASE_ID));

        verify(expected).getSchemas(jdbcConnection);
        for (AbstractDatabaseMetadataProvider other : List.of(postgresProvider, mySqlProvider, oracleProvider, sqlServerProvider)) {
            if (other != expected) {
                verify(other, never()).getSchemas(any());
            }
        }
    }

    private DatabaseMetadataServiceImpl newService() {
        lenient().when(postgresProvider.getDatabaseType()).thenReturn(DatabaseType.POSTGRESQL);
        lenient().when(mySqlProvider.getDatabaseType()).thenReturn(DatabaseType.MYSQL);
        lenient().when(oracleProvider.getDatabaseType()).thenReturn(DatabaseType.ORACLE);
        lenient().when(sqlServerProvider.getDatabaseType()).thenReturn(DatabaseType.SQL_SERVER);
        return new DatabaseMetadataServiceImpl(databaseRepository, connectionRepository, jdbcConnectionFactory,
                List.of(postgresProvider, mySqlProvider, oracleProvider, sqlServerProvider));
    }

    private void stubDatabaseAndConnection(DatabaseType databaseType) throws SQLException {
        Database database = database(databaseType);
        Connection connection = new Connection();
        connection.setConnectionId(1L);
        connection.setActive(true);
        when(databaseRepository.findById(DATABASE_ID)).thenReturn(Optional.of(database));
        when(connectionRepository.findActiveByDatabaseId(DATABASE_ID)).thenReturn(Optional.of(connection));
        when(jdbcConnectionFactory.openConnection(database, connection)).thenReturn(jdbcConnection);
    }

    private static Database database(DatabaseType databaseType) {
        Database database = new Database();
        database.setDatabaseId(DATABASE_ID);
        database.setDatabaseType(databaseType);
        database.setDatabaseHost("localhost");
        database.setDatabaseName("appdb");
        return database;
    }
}
