package com.databasemapper.service;

import com.databasemapper.dto.SwaggerEndpointResponse;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface SwaggerService {

    JsonNode fetchAndSave(Long serviceId);

    JsonNode commit(Long serviceId);

    List<SwaggerEndpointResponse> fetchSwaggerEndpoints(Long serviceId);
}
