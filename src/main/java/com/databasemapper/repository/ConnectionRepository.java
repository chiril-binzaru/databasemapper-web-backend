package com.databasemapper.repository;

import com.databasemapper.model.Connection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConnectionRepository extends JpaRepository<Connection, Long> {

    List<Connection> findAllByDatabase_DatabaseIdOrderByConnectionIdAsc(Long databaseId);
}
