package com.databasemapper.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ServiceResponse {

    private Long serviceId;
    private Long databaseId;
    private String serviceName;
    private String serviceBaseUrl;
    private String swaggerEndpoint;
}
