package com.databasemapper.service;

import com.databasemapper.dto.DatabaseRequest;
import com.databasemapper.dto.DatabaseResponse;
import com.databasemapper.model.Database;
import com.databasemapper.model.enums.DatabaseType;
import com.databasemapper.repository.DatabaseRepository;
import com.databasemapper.repository.ServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DatabaseServiceImplTest {

    @Mock
    private DatabaseRepository databaseRepository;

    @Mock
    private ServiceRepository serviceRepository;

    @InjectMocks
    private DatabaseServiceImpl databaseService;

    @Captor
    private ArgumentCaptor<Database> databaseCaptor;

    private static Database database(Long databaseId, DatabaseType type, String host, String name) {
        Database database = new Database();
        database.setDatabaseId(databaseId);
        database.setDatabaseType(type);
        database.setDatabaseHost(host);
        database.setDatabaseName(name);
        return database;
    }

    private static com.databasemapper.model.Service service(Long serviceId, Database database) {
        com.databasemapper.model.Service service = new com.databasemapper.model.Service();
        service.setServiceId(serviceId);
        service.setServiceName("Orders Service");
        service.setServiceBaseUrl("http://localhost:8080");
        service.setDatabase(database);
        return service;
    }

    private static DatabaseRequest request(DatabaseType type, String host, String name) {
        DatabaseRequest request = new DatabaseRequest();
        request.setDatabaseType(type);
        request.setDatabaseHost(host);
        request.setDatabaseName(name);
        return request;
    }

    // ---------------------------------------------------------------- update

    @Test
    void updateWritesAllFieldsOntoExistingDatabaseAndReturnsUpdatedResponse() {
        Long serviceId = 1L;
        Long databaseId = 7L;

        Database existingDatabase = database(databaseId, DatabaseType.POSTGRESQL, "old-host", "old-db");
        com.databasemapper.model.Service existingService = service(serviceId, existingDatabase);

        DatabaseRequest request = request(DatabaseType.MYSQL, "new-host", "new-db");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(existingService));
        when(databaseRepository.save(any(Database.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DatabaseResponse response = databaseService.update(serviceId, request);

        verify(databaseRepository).save(databaseCaptor.capture());
        Database saved = databaseCaptor.getValue();

        assertEquals(DatabaseType.MYSQL, saved.getDatabaseType());
        assertEquals("new-host", saved.getDatabaseHost());
        assertEquals("new-db", saved.getDatabaseName());

        assertEquals(databaseId, response.getDatabaseId());
        assertEquals(DatabaseType.MYSQL, response.getDatabaseType());
        assertEquals("new-host", response.getDatabaseHost());
        assertEquals("new-db", response.getDatabaseName());
    }

    @Test
    void updateMutatesAssignedDatabaseInPlaceAndDoesNotCreateNewEntity() {
        Long serviceId = 1L;
        Long databaseId = 7L;

        Database existingDatabase = database(databaseId, DatabaseType.POSTGRESQL, "old-host", "old-db");
        com.databasemapper.model.Service existingService = service(serviceId, existingDatabase);

        DatabaseRequest request = request(DatabaseType.ORACLE, "new-host", "new-db");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(existingService));
        when(databaseRepository.save(any(Database.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DatabaseResponse response = databaseService.update(serviceId, request);

        verify(databaseRepository).save(databaseCaptor.capture());
        Database saved = databaseCaptor.getValue();

        // The saved entity must be the very instance already assigned to the service:
        // the databases row can be shared by several services, so update is in-place,
        // never copy-on-write.
        assertSame(existingDatabase, saved);
        assertSame(existingDatabase, existingService.getDatabase());
        assertEquals(databaseId, existingDatabase.getDatabaseId());
        assertEquals(databaseId, response.getDatabaseId());

        // The service association itself is untouched by update.
        verify(serviceRepository, never()).save(any());
    }

    @Test
    void updateThrowsEntityNotFoundExceptionWhenServiceDoesNotExist() {
        Long serviceId = 99L;
        DatabaseRequest request = request(DatabaseType.POSTGRESQL, "host", "db");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.empty());

        EntityNotFoundException exception =
                assertThrows(EntityNotFoundException.class, () -> databaseService.update(serviceId, request));

        assertEquals("Service not found with id: 99", exception.getMessage());
        verify(databaseRepository, never()).save(any());
    }

    @Test
    void updateThrowsNotFoundWhenServiceHasNoDatabaseAssigned() {
        Long serviceId = 3L;
        com.databasemapper.model.Service existingService = service(serviceId, null);
        DatabaseRequest request = request(DatabaseType.POSTGRESQL, "host", "db");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(existingService));

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> databaseService.update(serviceId, request));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertNotNull(exception.getReason());
        assertTrue(exception.getReason().contains("has no database assigned"),
                "Unexpected reason: " + exception.getReason());
        verify(databaseRepository, never()).save(any());
    }

    // --------------------------------------------------------- getByServiceId

    @Test
    void getByServiceIdReturnsAssignedDatabase() {
        Long serviceId = 1L;
        Long databaseId = 42L;

        Database assignedDatabase = database(databaseId, DatabaseType.SQL_SERVER, "db-host", "orders");
        com.databasemapper.model.Service existingService = service(serviceId, assignedDatabase);

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(existingService));

        DatabaseResponse response = databaseService.getByServiceId(serviceId);

        assertEquals(databaseId, response.getDatabaseId());
        assertEquals(DatabaseType.SQL_SERVER, response.getDatabaseType());
        assertEquals("db-host", response.getDatabaseHost());
        assertEquals("orders", response.getDatabaseName());
        verify(databaseRepository, never()).save(any());
    }

    @Test
    void getByServiceIdThrowsEntityNotFoundExceptionWhenServiceDoesNotExist() {
        Long serviceId = 99L;

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.empty());

        EntityNotFoundException exception =
                assertThrows(EntityNotFoundException.class, () -> databaseService.getByServiceId(serviceId));

        assertEquals("Service not found with id: 99", exception.getMessage());
    }

    @Test
    void getByServiceIdThrowsNotFoundWhenServiceHasNoDatabaseAssigned() {
        Long serviceId = 5L;
        com.databasemapper.model.Service existingService = service(serviceId, null);

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(existingService));

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> databaseService.getByServiceId(serviceId));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        assertNotNull(exception.getReason());
        assertTrue(exception.getReason().contains("has no database assigned"),
                "Unexpected reason: " + exception.getReason());
    }

    // ---------------------------------------------------------------- create

    @Test
    void createSavesNewDatabaseAndAssignsItToService() {
        Long serviceId = 1L;
        Long generatedDatabaseId = 10L;

        com.databasemapper.model.Service existingService = service(serviceId, null);
        DatabaseRequest request = request(DatabaseType.POSTGRESQL, "new-host", "new-db");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(existingService));
        when(databaseRepository.save(any(Database.class))).thenAnswer(invocation -> {
            Database toSave = invocation.getArgument(0);
            toSave.setDatabaseId(generatedDatabaseId);
            return toSave;
        });

        DatabaseResponse response = databaseService.create(serviceId, request);

        verify(databaseRepository).save(databaseCaptor.capture());
        Database saved = databaseCaptor.getValue();

        assertEquals(DatabaseType.POSTGRESQL, saved.getDatabaseType());
        assertEquals("new-host", saved.getDatabaseHost());
        assertEquals("new-db", saved.getDatabaseName());

        // The service is repointed at the freshly saved database.
        assertSame(saved, existingService.getDatabase());
        verify(serviceRepository).save(existingService);

        assertEquals(generatedDatabaseId, response.getDatabaseId());
        assertEquals(DatabaseType.POSTGRESQL, response.getDatabaseType());
        assertEquals("new-host", response.getDatabaseHost());
        assertEquals("new-db", response.getDatabaseName());
    }

    @Test
    void createThrowsEntityNotFoundExceptionWhenServiceDoesNotExist() {
        Long serviceId = 99L;
        DatabaseRequest request = request(DatabaseType.POSTGRESQL, "host", "db");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.empty());

        EntityNotFoundException exception =
                assertThrows(EntityNotFoundException.class, () -> databaseService.create(serviceId, request));

        assertEquals("Service not found with id: 99", exception.getMessage());
        verify(databaseRepository, never()).save(any());
        verify(serviceRepository, never()).save(any());
    }

    @Test
    void createThrowsConflictWhenServiceAlreadyHasDatabaseAssigned() {
        Long serviceId = 1L;

        Database assignedDatabase = database(4L, DatabaseType.MYSQL, "existing-host", "existing-db");
        com.databasemapper.model.Service existingService = service(serviceId, assignedDatabase);
        DatabaseRequest request = request(DatabaseType.POSTGRESQL, "host", "db");

        when(serviceRepository.findById(serviceId)).thenReturn(Optional.of(existingService));

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> databaseService.create(serviceId, request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertNotNull(exception.getReason());
        assertTrue(exception.getReason().contains("already has a database assigned"),
                "Unexpected reason: " + exception.getReason());
        verify(databaseRepository, never()).save(any());
        verify(serviceRepository, never()).save(any());
        assertSame(assignedDatabase, existingService.getDatabase());
    }
}
