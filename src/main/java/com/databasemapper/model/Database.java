package com.databasemapper.model;

import com.databasemapper.model.enums.DatabaseType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "databases")
@Getter
@Setter
public class Database {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "database_id")
    private Long databaseId;

    @Enumerated(EnumType.STRING)
    @Column(name = "database_type", nullable = false, length = 50)
    private DatabaseType databaseType;

    @Column(name = "database_host", nullable = false)
    private String databaseHost;

    @Column(name = "database_name", nullable = false)
    private String databaseName;
}
