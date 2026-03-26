package com.databasemapper.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "mappings")
@Getter
@Setter
public class Mapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mapping_id")
    private Long mappingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "endpoint_id", nullable = false)
    private Endpoint endpoint;

    @Column(name = "response_model_name", nullable = false)
    private String responseModelName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "mapping_data", columnDefinition = "jsonb")
    private String mappingData;
}
