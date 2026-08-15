package com.databasemapper.config;

import com.databasemapper.model.Connection;
import com.databasemapper.model.Database;
import com.databasemapper.model.enums.ConnectionMode;
import com.databasemapper.model.enums.DatabaseType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.DriverManager;
import java.sql.SQLException;

@Component
public class JdbcConnectionFactory {

    private final boolean sqlServerEncrypt;
    private final boolean sqlServerTrustServerCertificate;

    public JdbcConnectionFactory(
            @Value("${databasemapper.jdbc.sqlserver.encrypt:false}") boolean sqlServerEncrypt,
            @Value("${databasemapper.jdbc.sqlserver.trust-server-certificate:true}") boolean sqlServerTrustServerCertificate) {
        this.sqlServerEncrypt = sqlServerEncrypt;
        this.sqlServerTrustServerCertificate = sqlServerTrustServerCertificate;
    }

    public java.sql.Connection openConnection(Database database, Connection connection) throws SQLException {
        if (connection.getConnectionMode() == ConnectionMode.CONNECTION_STRING) {
            String username = connection.getUsername();
            if (username != null && !username.isBlank()) {
                return DriverManager.getConnection(connection.getConnectionString(), username, connection.getPassword());
            }
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
            case SQL_SERVER -> "jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=%s;trustServerCertificate=%s"
                    .formatted(host, port, dbName, sqlServerEncrypt, sqlServerTrustServerCertificate);
        };
    }

    /**
     * The URL scheme every connection string for this vendor must start with, so a string pasted for
     * the wrong vendor is rejected on save rather than surfacing later as an unrelated SQL error.
     */
    public String expectedUrlPrefix(DatabaseType databaseType) {
        return switch (databaseType) {
            case POSTGRESQL -> "jdbc:postgresql:";
            case MYSQL      -> "jdbc:mysql:";
            case ORACLE     -> "jdbc:oracle:";
            case SQL_SERVER -> "jdbc:sqlserver:";
        };
    }
}
