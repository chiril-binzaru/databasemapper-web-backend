package com.databasemapper.service;

import com.databasemapper.dto.DatabaseRequest;
import com.databasemapper.dto.DatabaseResponse;
import com.databasemapper.model.Database;
import com.databasemapper.repository.DatabaseRepository;
import com.databasemapper.repository.ServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class DatabaseServiceImpl implements DatabaseService {

    private final DatabaseRepository databaseRepository;
    private final ServiceRepository serviceRepository;

    @Override
    @Transactional(readOnly = true)
    public DatabaseResponse getByServiceId(Long serviceId) {
        return toResponse(requireAssignedDatabase(serviceId));
    }

    @Override
    @Transactional
    public DatabaseResponse create(Long serviceId, DatabaseRequest request) {
        com.databasemapper.model.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("Service not found with id: " + serviceId));

        if (service.getDatabase() != null) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Service already has a database assigned: " + serviceId);
        }

        Database database = new Database();
        database.setDatabaseType(request.getDatabaseType());
        database.setDatabaseHost(request.getDatabaseHost());
        database.setDatabaseName(request.getDatabaseName());

        Database saved = databaseRepository.save(database);
        service.setDatabase(saved);
        serviceRepository.save(service);

        return toResponse(saved);
    }

    @Override
    @Transactional
    public DatabaseResponse update(Long serviceId, DatabaseRequest request) {
        Database database = requireAssignedDatabase(serviceId);

        database.setDatabaseType(request.getDatabaseType());
        database.setDatabaseHost(request.getDatabaseHost());
        database.setDatabaseName(request.getDatabaseName());

        return toResponse(databaseRepository.save(database));
    }

    private Database requireAssignedDatabase(Long serviceId) {
        com.databasemapper.model.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("Service not found with id: " + serviceId));

        Database database = service.getDatabase();
        if (database == null) {
            throw new ResponseStatusException(
                    NOT_FOUND,
                    "Service " + serviceId + " has no database assigned");
        }

        return database;
    }

    private DatabaseResponse toResponse(Database database) {
        return DatabaseResponse.builder()
                .databaseId(database.getDatabaseId())
                .databaseType(database.getDatabaseType())
                .databaseHost(database.getDatabaseHost())
                .databaseName(database.getDatabaseName())
                .build();
    }
}
