package com.databasemapper.config;

import com.databasemapper.model.Connection;
import com.databasemapper.model.Database;
import com.databasemapper.model.enums.ConnectionMode;
import org.springframework.stereotype.Component;

import java.sql.DriverManager;
import java.sql.SQLException;

@Component
public class JdbcConnectionFactory {

    public java.sql.Connection openConnection(Database database, Connection connection) throws SQLException {
        if (connection.getConnectionMode() == ConnectionMode.CONNECTION_STRING) {
            return DriverManager.getConnection(connection.getConnectionString());
        }
        return DriverManager.getConnection(buildJdbcUrl(database, connection), connection.getUsername(), connection.getPassword());
    }

    public String buildJdbcUrl(Database database, Connection connection) {
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
}
