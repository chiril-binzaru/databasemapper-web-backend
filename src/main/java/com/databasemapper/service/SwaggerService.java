package com.databasemapper.service;

import com.databasemapper.dto.SwaggerEndpointResponse;

import java.util.List;

public interface SwaggerService {

    List<SwaggerEndpointResponse> fetchSwaggerEndpoints(Long serviceId);
}
