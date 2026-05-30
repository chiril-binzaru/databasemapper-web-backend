package com.databasemapper.service;

import com.databasemapper.dto.DataFetchRequest;
import com.databasemapper.model.HttpMethod;
import com.fasterxml.jackson.databind.JsonNode;

public interface MappingHydrationService {

    JsonNode fetchData(Long endpointId, DataFetchRequest request);

    JsonNode fetchData(String serviceName, String endpointPath, HttpMethod httpMethod, DataFetchRequest request);
}
