package com.databasemapper.repository;

import com.databasemapper.model.Endpoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EndpointRepository extends JpaRepository<Endpoint, Long> {

    List<Endpoint> findAllByService_ServiceIdOrderByEndpointPathAscHttpMethodAsc(Long serviceId);
}
