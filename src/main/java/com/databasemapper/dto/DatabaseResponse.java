package com.databasemapper.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DatabaseResponse {

    private Long databaseId;
    private String databaseType;
    private String databaseHost;
    private String databaseName;
}
