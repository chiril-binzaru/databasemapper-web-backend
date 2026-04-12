package com.databasemapper.service;

import com.databasemapper.dto.ConnectionRequest;
import com.databasemapper.dto.ConnectionResponse;
import com.databasemapper.model.Connection;
import com.databasemapper.model.Database;
import com.databasemapper.model.enums.ConnectionMode;
import com.databasemapper.repository.ConnectionRepository;
import com.databasemapper.repository.DatabaseRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ConnectionServiceImpl implements ConnectionService {

    private final ConnectionRepository connectionRepository;
    private final DatabaseRepository databaseRepository;

    @Override
    public List<ConnectionResponse> getByDatabaseId(Long databaseId) {
        requireDatabase(databaseId);

        return connectionRepository
                .findAllByDatabase_DatabaseIdOrderByConnectionIdAsc(databaseId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ConnectionResponse create(Long databaseId, ConnectionRequest request) {
        Database database = requireDatabase(databaseId);

        Connection connection = new Connection();
        connection.setDatabase(database);
        connection.setConnectionMode(request.getConnectionMode());
        connection.setPort(request.getPort());
        connection.setUsername(request.getUsername());
        connection.setPassword(request.getPassword());
        connection.setConnectionString(request.getConnectionString());

        return toResponse(connectionRepository.save(connection));
    }

    @Override
    public ConnectionResponse update(Long databaseId, Long connectionId, ConnectionRequest request) {
        requireDatabase(databaseId);

        Connection connection = connectionRepository
                .findByConnectionIdAndDatabase_DatabaseId(connectionId, databaseId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Connection not found with id: " + connectionId + " for database: " + databaseId));

        connection.setConnectionMode(request.getConnectionMode());

        if (request.getConnectionMode() == ConnectionMode.PARAMETERS) {
            connection.setPort(request.getPort());
            connection.setUsername(request.getUsername());
            connection.setPassword(request.getPassword());
            connection.setConnectionString(null);
        } else {
            connection.setConnectionString(request.getConnectionString());
            connection.setPort(null);
            connection.setUsername(null);
            connection.setPassword(null);
        }

        return toResponse(connectionRepository.save(connection));
    }

    private Database requireDatabase(Long databaseId) {
        return databaseRepository.findById(databaseId)
                .orElseThrow(() -> new EntityNotFoundException("Database not found with id: " + databaseId));
    }

    private ConnectionResponse toResponse(Connection connection) {
        return ConnectionResponse.builder()
                .connectionId(connection.getConnectionId())
                .databaseId(connection.getDatabase().getDatabaseId())
                .port(connection.getPort())
                .username(connection.getUsername())
                .connectionString(connection.getConnectionString())
                .connectionMode(connection.getConnectionMode())
                .isActive(connection.isActive())
                .build();
    }
}
