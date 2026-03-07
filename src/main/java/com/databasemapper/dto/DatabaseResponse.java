package com.databasemapper.dto;

import com.databasemapper.model.enums.DatabaseType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DatabaseResponse {

    private Long databaseId;
    private DatabaseType databaseType;
    private String databaseHost;
    private String databaseName;
}
