package com.databasemapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_fetched_swagger", columnDefinition = "jsonb")
    private JsonNode lastFetchedSwagger;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "committed_swagger", columnDefinition = "jsonb")
    private JsonNode committedSwagger;
}
