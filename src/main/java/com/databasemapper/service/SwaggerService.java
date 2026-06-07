package com.databasemapper.service;

import com.databasemapper.dto.SwaggerEndpointResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;

public interface SwaggerService {

    record EndpointModelResult(JsonNode responseModel, boolean isList) {}

    List<SwaggerEndpointResponse> fetchSwaggerEndpoints(Long serviceId);

    Map<String, EndpointModelResult> fetchResponseModels(Long serviceId);
}
