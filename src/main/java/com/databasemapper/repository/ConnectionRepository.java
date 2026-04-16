package com.databasemapper.repository;

import com.databasemapper.model.Connection;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ConnectionRepository extends JpaRepository<Connection, Long> {

    List<Connection> findAllByDatabase_DatabaseIdOrderByConnectionIdAsc(Long databaseId);

    java.util.Optional<Connection> findByConnectionIdAndDatabase_DatabaseId(Long connectionId, Long databaseId);

    @Modifying
    @Transactional
    @Query("UPDATE Connection c SET c.isActive = false WHERE c.database.databaseId = :databaseId")
    void deactivateAllByDatabaseId(@Param("databaseId") Long databaseId);
}
