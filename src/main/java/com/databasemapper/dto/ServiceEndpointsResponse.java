package com.databasemapper.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ServiceEndpointsResponse {

    private Long serviceId;
    private List<EndpointResponse> endpoints;
}
