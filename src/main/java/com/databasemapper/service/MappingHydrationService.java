package com.databasemapper.service;

import com.databasemapper.dto.DataFetchRequest;
import com.fasterxml.jackson.databind.JsonNode;

public interface MappingHydrationService {

    JsonNode fetchData(Long endpointId, DataFetchRequest request);
}
