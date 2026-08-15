package com.databasemapper.service;

import com.databasemapper.config.JdbcConnectionFactory;
import com.databasemapper.dto.DbColumnResponse;
import com.databasemapper.model.Connection;
import com.databasemapper.model.Database;
import com.databasemapper.model.enums.DatabaseType;
import com.databasemapper.repository.ConnectionRepository;
import com.databasemapper.repository.DatabaseRepository;
import com.databasemapper.service.metadata.AbstractDatabaseMetadataProvider;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class DatabaseMetadataServiceImpl implements DatabaseMetadataService {

    private final DatabaseRepository databaseRepository;
    private final ConnectionRepository connectionRepository;
    private final JdbcConnectionFactory jdbcConnectionFactory;
    private final Map<DatabaseType, AbstractDatabaseMetadataProvider> providers;

    public DatabaseMetadataServiceImpl(DatabaseRepository databaseRepository,
                                       ConnectionRepository connectionRepository,
                                       JdbcConnectionFactory jdbcConnectionFactory,
                                       List<AbstractDatabaseMetadataProvider> providerList) {
        this.databaseRepository = databaseRepository;
        this.connectionRepository = connectionRepository;
        this.jdbcConnectionFactory = jdbcConnectionFactory;
        this.providers = indexByDatabaseType(providerList);
    }

    @Override
    public List<String> getSchemas(Long databaseId) {
        Database database = requireDatabase(databaseId);
        Connection connection = requireActiveConnection(databaseId);
        AbstractDatabaseMetadataProvider provider = requireProvider(database.getDatabaseType());

        try (java.sql.Connection conn = jdbcConnectionFactory.openConnection(database, connection)) {
            return provider.getSchemas(conn);
        } catch (SQLException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Failed to fetch schemas from database: " + e.getMessage());
        }
    }

    @Override
    public List<String> getTables(Long databaseId, String schemaName) {
        Database database = requireDatabase(databaseId);
        Connection connection = requireActiveConnection(databaseId);
        AbstractDatabaseMetadataProvider provider = requireProvider(database.getDatabaseType());

        try (java.sql.Connection conn = jdbcConnectionFactory.openConnection(database, connection)) {
            return provider.getTables(conn, schemaName);
        } catch (SQLException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Failed to fetch tables from database: " + e.getMessage());
        }
    }

    @Override
    public List<DbColumnResponse> getColumns(Long databaseId, String schemaName, String tableName) {
        Database database = requireDatabase(databaseId);
        Connection connection = requireActiveConnection(databaseId);
        AbstractDatabaseMetadataProvider provider = requireProvider(database.getDatabaseType());

        try (java.sql.Connection conn = jdbcConnectionFactory.openConnection(database, connection)) {
            return provider.getColumns(conn, schemaName, tableName);
        } catch (SQLException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Failed to fetch columns from database: " + e.getMessage());
        }
    }

    private static Map<DatabaseType, AbstractDatabaseMetadataProvider> indexByDatabaseType(List<AbstractDatabaseMetadataProvider> providerList) {
        Map<DatabaseType, AbstractDatabaseMetadataProvider> index = new EnumMap<>(DatabaseType.class);
        for (AbstractDatabaseMetadataProvider provider : providerList) {
            AbstractDatabaseMetadataProvider previous = index.put(provider.getDatabaseType(), provider);
            if (previous != null) {
                throw new IllegalStateException("Multiple AbstractDatabaseMetadataProvider beans registered for database type %s: %s and %s"
                        .formatted(provider.getDatabaseType(),
                                previous.getClass().getName(),
                                provider.getClass().getName()));
            }
        }

        Set<DatabaseType> missing = EnumSet.allOf(DatabaseType.class);
        missing.removeAll(index.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("No AbstractDatabaseMetadataProvider bean registered for database types: " + missing);
        }
        return index;
    }

    private AbstractDatabaseMetadataProvider requireProvider(DatabaseType databaseType) {
        AbstractDatabaseMetadataProvider provider = providers.get(databaseType);
        if (provider == null) {
            // Unreachable: the constructor rejects a registry that is missing any DatabaseType. Kept so
            // that relaxing that guard surfaces as a named failure rather than a NullPointerException.
            throw new IllegalStateException("No metadata provider registered for database type: " + databaseType);
        }
        return provider;
    }

    private Database requireDatabase(Long databaseId) {
        return databaseRepository.findById(databaseId)
                .orElseThrow(() -> new EntityNotFoundException("Database not found with id: " + databaseId));
    }

    private Connection requireActiveConnection(Long databaseId) {
        return connectionRepository.findActiveByDatabaseId(databaseId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "No active connection found for database: " + databaseId));
    }
}
