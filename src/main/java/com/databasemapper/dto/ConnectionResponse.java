package com.databasemapper.dto;

import com.databasemapper.model.enums.ConnectionMode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ConnectionResponse {

    private Long connectionId;
    private Long databaseId;
    private Integer port;
    private String username;
    private String connectionString;
    private ConnectionMode connectionMode;
    private boolean isActive;
}
