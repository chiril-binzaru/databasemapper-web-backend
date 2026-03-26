package com.databasemapper.repository;

import com.databasemapper.model.Mapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MappingRepository extends JpaRepository<Mapping, Long> {

    void deleteByEndpoint_EndpointIdIn(List<Long> endpointIds);
}
