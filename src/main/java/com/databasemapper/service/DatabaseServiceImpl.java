package com.databasemapper.service;

import com.databasemapper.dto.DatabaseRequest;
import com.databasemapper.dto.DatabaseResponse;
import com.databasemapper.model.Database;
import com.databasemapper.repository.DatabaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DatabaseServiceImpl implements DatabaseService {

    private final DatabaseRepository databaseRepository;

    @Override
    public DatabaseResponse create(DatabaseRequest request) {
        Database database = new Database();
        database.setDatabaseType(request.getDatabaseType());
        database.setDatabaseHost(request.getDatabaseHost());
        database.setDatabaseName(request.getDatabaseName());

        Database saved = databaseRepository.save(database);

        return DatabaseResponse.builder()
                .databaseId(saved.getDatabaseId())
                .databaseType(saved.getDatabaseType())
                .databaseHost(saved.getDatabaseHost())
                .databaseName(saved.getDatabaseName())
                .build();
    }
}
