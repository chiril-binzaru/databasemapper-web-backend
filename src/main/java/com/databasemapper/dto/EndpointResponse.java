package com.databasemapper.dto;

import com.databasemapper.model.HttpMethod;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EndpointResponse {

    private Long endpointId;
    private HttpMethod httpMethod;
    private String endpointPath;
}
