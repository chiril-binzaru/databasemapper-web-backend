package com.databasemapper.service;

import com.databasemapper.model.Endpoint;
import com.databasemapper.repository.EndpointRepository;
import com.databasemapper.repository.MappingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EndpointMappingServiceImpl implements EndpointMappingService {

    private final EndpointRepository endpointRepository;
    private final MappingRepository mappingRepository;

    @Override
    public JsonNode getMappingData(Long endpointId) {
        requireEndpoint(endpointId);

        return mappingRepository.findByEndpoint_EndpointId(endpointId)
                .map(mapping -> mapping.getMappingData())
                .orElse(null);
    }

    @Override
    public JsonNode getResponseModel(Long endpointId) {
        return requireEndpoint(endpointId).getResponseModel();
    }

    private Endpoint requireEndpoint(Long endpointId) {
        return endpointRepository.findById(endpointId)
                .orElseThrow(() -> new EntityNotFoundException("Endpoint not found with id: " + endpointId));
    }
}
