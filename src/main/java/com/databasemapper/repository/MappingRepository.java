package com.databasemapper.repository;

import com.databasemapper.model.Mapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MappingRepository extends JpaRepository<Mapping, Long> {

    void deleteByEndpoint_EndpointIdIn(List<Long> endpointIds);

    Optional<Mapping> findByEndpoint_EndpointId(Long endpointId);
}
