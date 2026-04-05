package com.databasemapper.dto;

import com.databasemapper.model.HttpMethod;
import com.databasemapper.model.enums.EndpointSyncStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EndpointSyncItemResponse {

    private HttpMethod httpMethod;
    private String path;
    private EndpointSyncStatus status;
}
