package com.databasemapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "endpoints")
@Getter
@Setter
public class Endpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "endpoint_id")
    private Long endpointId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Enumerated(EnumType.STRING)
    @Column(name = "http_method", nullable = false)
    private HttpMethod httpMethod;

    @Column(name = "endpoint_path", nullable = false)
    private String endpointPath;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_model", columnDefinition = "jsonb")
    private JsonNode responseModel;

    @Column(name = "is_list_response", nullable = false)
    private boolean listResponse;
}
