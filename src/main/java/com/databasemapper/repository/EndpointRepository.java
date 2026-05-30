package com.databasemapper.repository;

import com.databasemapper.model.Endpoint;
import com.databasemapper.model.HttpMethod;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EndpointRepository extends JpaRepository<Endpoint, Long> {

    List<Endpoint> findAllByService_ServiceIdOrderByEndpointPathAscHttpMethodAsc(Long serviceId);

    void deleteByService_ServiceId(Long serviceId);

    @Query("SELECT e FROM Endpoint e JOIN FETCH e.service s LEFT JOIN FETCH s.database WHERE e.endpointId = :id")
    Optional<Endpoint> findByIdWithServiceAndDatabase(@Param("id") Long id);

    @Query("SELECT e FROM Endpoint e JOIN FETCH e.service s LEFT JOIN FETCH s.database WHERE s.serviceName = :serviceName AND e.endpointPath = :endpointPath AND e.httpMethod = :httpMethod")
    Optional<Endpoint> findByServiceNameAndEndpointPathAndHttpMethod(
            @Param("serviceName") String serviceName,
            @Param("endpointPath") String endpointPath,
            @Param("httpMethod") HttpMethod httpMethod);
}
