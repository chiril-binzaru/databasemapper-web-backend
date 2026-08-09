package com.databasemapper.model;

import com.databasemapper.model.enums.ConnectionMode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "connections")
@Getter
@Setter
public class Connection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "connection_id")
    private Long connectionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "database_id", nullable = false)
    private Database database;

    @Column(name = "port")
    private Integer port;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "connection_string")
    private String connectionString;

    @Enumerated(EnumType.STRING)
    @Column(name = "connection_mode", nullable = false, length = 50)
    private ConnectionMode connectionMode;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;
}
