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

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class DatabaseServiceImpl implements DatabaseService {

    private final DatabaseRepository databaseRepository;
    private final ServiceRepository serviceRepository;

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

        return DatabaseResponse.builder()
                .databaseId(saved.getDatabaseId())
                .databaseType(saved.getDatabaseType())
                .databaseHost(saved.getDatabaseHost())
                .databaseName(saved.getDatabaseName())
                .build();
    }
}
