package com.databasemapper.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "services")
@Getter
@Setter
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "service_id")
    private Long serviceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "database_id")
    private Database database;

    @Column(name = "service_name", nullable = false)
    private String serviceName;

    @Column(name = "service_base_url", nullable = false)
    private String serviceBaseUrl;

    @Column(name = "swagger_endpoint")
    private String swaggerEndpoint;
}
