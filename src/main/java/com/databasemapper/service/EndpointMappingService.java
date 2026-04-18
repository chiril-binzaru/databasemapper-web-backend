package com.databasemapper.service;

import com.fasterxml.jackson.databind.JsonNode;

public interface EndpointMappingService {

    JsonNode getMappingData(Long endpointId);

    JsonNode getResponseModel(Long endpointId);
}
