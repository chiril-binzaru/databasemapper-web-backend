package com.databasemapper.service;

import com.databasemapper.dto.SwaggerEndpointResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public interface SwaggerService {

    List<SwaggerEndpointResponse> fetchSwaggerEndpoints(Long serviceId);

    Map<String, JsonNode> fetchResponseModels(Long serviceId);
}
