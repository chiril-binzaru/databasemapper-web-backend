package com.databasemapper.service;

import com.databasemapper.dto.ConnectionRequest;
import com.databasemapper.dto.ConnectionResponse;
import com.databasemapper.model.Connection;
import com.databasemapper.model.Database;
import com.databasemapper.model.enums.ConnectionMode;
import com.databasemapper.repository.ConnectionRepository;
import com.databasemapper.repository.DatabaseRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.DriverManager;
import java.sql.SQLException;

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

        Connection connection = requireConnection(connectionId, databaseId);

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

    @Override
    public void testConnection(Long databaseId, Long connectionId) {
        Database database = requireDatabase(databaseId);
        Connection connection = requireConnection(connectionId, databaseId);
        attemptJdbcConnection(database, connection);
    }

    @Transactional
    @Override
    public ConnectionResponse activateConnection(Long databaseId, Long connectionId) {
        Database database = requireDatabase(databaseId);
        Connection connection = requireConnection(connectionId, databaseId);

        attemptJdbcConnection(database, connection);

        connectionRepository.deactivateAllByDatabaseId(databaseId);
        connection.setActive(true);
        return toResponse(connectionRepository.save(connection));
    }

    @Override
    public void delete(Long databaseId, Long connectionId) {
        requireDatabase(databaseId);
        Connection connection = requireConnection(connectionId, databaseId);
        connectionRepository.delete(connection);
    }

    private void attemptJdbcConnection(Database database, Connection connection) {
        try {
            if (connection.getConnectionMode() == ConnectionMode.CONNECTION_STRING) {
                try (var ignored = DriverManager.getConnection(connection.getConnectionString())) { }
            } else {
                String url = buildJdbcUrl(database, connection);
                try (var ignored = DriverManager.getConnection(url, connection.getUsername(), connection.getPassword())) { }
            }
        } catch (SQLException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Could not connect to database: " + e.getMessage());
        }
    }

    private String buildJdbcUrl(Database database, Connection connection) {
        String host = database.getDatabaseHost();
        int port = connection.getPort();
        String dbName = database.getDatabaseName();
        return switch (database.getDatabaseType()) {
            case POSTGRESQL -> "jdbc:postgresql://%s:%d/%s".formatted(host, port, dbName);
            case MYSQL      -> "jdbc:mysql://%s:%d/%s".formatted(host, port, dbName);
            case ORACLE     -> "jdbc:oracle:thin:@//%s:%d/%s".formatted(host, port, dbName);
            case SQL_SERVER -> "jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=false".formatted(host, port, dbName);
        };
    }

    private Database requireDatabase(Long databaseId) {
        return databaseRepository.findById(databaseId)
                .orElseThrow(() -> new EntityNotFoundException("Database not found with id: " + databaseId));
    }

    private Connection requireConnection(Long connectionId, Long databaseId) {
        return connectionRepository
                .findByConnectionIdAndDatabase_DatabaseId(connectionId, databaseId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Connection not found with id: " + connectionId + " for database: " + databaseId));
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
