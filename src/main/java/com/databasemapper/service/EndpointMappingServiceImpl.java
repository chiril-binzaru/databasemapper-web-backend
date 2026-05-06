package com.databasemapper.service;

import com.databasemapper.dto.MappingDto;
import com.databasemapper.model.Endpoint;
import com.databasemapper.model.Mapping;
import com.databasemapper.repository.EndpointRepository;
import com.databasemapper.repository.MappingRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EndpointMappingServiceImpl implements EndpointMappingService {

    private final EndpointRepository endpointRepository;
    private final MappingRepository mappingRepository;
    private final ObjectMapper objectMapper;

    @Override
    public MappingDto getMappingData(Long endpointId) {
        requireEndpoint(endpointId);
        return mappingRepository.findByEndpoint_EndpointId(endpointId)
                .map(mapping -> deserialize(mapping.getMappingData()))
                .orElse(null);
    }

    @Override
    public JsonNode getResponseModel(Long endpointId) {
        return requireEndpoint(endpointId).getResponseModel();
    }

    @Override
    public MappingDto saveMapping(Long endpointId, MappingDto request) {
        Endpoint endpoint = requireEndpoint(endpointId);

        if (mappingRepository.findByEndpoint_EndpointId(endpointId).isPresent()) {
            throw new DataIntegrityViolationException("Mapping already exists for endpoint id: " + endpointId);
        }

        Mapping mapping = new Mapping();
        mapping.setEndpoint(endpoint);
        mapping.setMappingData(objectMapper.valueToTree(request));

        return deserialize(mappingRepository.save(mapping).getMappingData());
    }

    @Override
    public MappingDto replaceMapping(Long endpointId, MappingDto request) {
        requireEndpoint(endpointId);

        Mapping mapping = mappingRepository.findByEndpoint_EndpointId(endpointId)
                .orElseThrow(() -> new EntityNotFoundException("Mapping not found for endpoint id: " + endpointId));

        mapping.setMappingData(objectMapper.valueToTree(request));

        return deserialize(mappingRepository.save(mapping).getMappingData());
    }

    private Endpoint requireEndpoint(Long endpointId) {
        return endpointRepository.findById(endpointId)
                .orElseThrow(() -> new EntityNotFoundException("Endpoint not found with id: " + endpointId));
    }

    private MappingDto deserialize(JsonNode node) {
        try {
            return objectMapper.treeToValue(node, MappingDto.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Stored mapping data could not be deserialized", e);
        }
    }
}
