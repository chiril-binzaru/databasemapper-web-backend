package com.databasemapper.service;

import com.databasemapper.dto.MappingDto;
import com.fasterxml.jackson.databind.JsonNode;

public interface EndpointMappingService {

    MappingDto getMappingData(Long endpointId);

    JsonNode getResponseModel(Long endpointId);

    MappingDto saveMapping(Long endpointId, MappingDto request);

    MappingDto replaceMapping(Long endpointId, MappingDto request);
}
