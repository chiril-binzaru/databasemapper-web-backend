package com.databasemapper.dto;

import com.databasemapper.model.enums.SyncStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EndpointSyncResponse {

    private SyncStatus status;
    private List<EndpointSyncItemResponse> endpoints;
}
