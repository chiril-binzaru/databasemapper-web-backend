package com.databasemapper.service;

import com.databasemapper.dto.DbColumnResponse;

import java.util.List;

public interface DatabaseMetadataService {
    List<String> getSchemas(Long databaseId);
    List<String> getTables(Long databaseId, String schemaName);
    List<DbColumnResponse> getColumns(Long databaseId, String schemaName, String tableName);
}
