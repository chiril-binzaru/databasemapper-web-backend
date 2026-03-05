package com.databasemapper.model;

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

    @Column(name = "database_type", nullable = false, length = 50)
    private String databaseType;

    @Column(name = "database_host", nullable = false)
    private String databaseHost;

    @Column(name = "database_name", nullable = false)
    private String databaseName;
}
