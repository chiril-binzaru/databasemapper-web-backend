package com.databasemapper.service;

import com.databasemapper.dto.SwaggerEndpointResponse;
import com.databasemapper.model.HttpMethod;
import com.databasemapper.repository.ServiceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SwaggerServiceImpl implements SwaggerService {

    private final ServiceRepository serviceRepository;
    private final RestClient restClient;

    @Override
    public List<SwaggerEndpointResponse> fetchSwaggerEndpoints(Long serviceId) {
        com.databasemapper.model.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("Service not found with id: " + serviceId));

        JsonNode swagger = fetchRawSwagger(service);
        return parseEndpoints(swagger);
    }

    private JsonNode fetchRawSwagger(com.databasemapper.model.Service service) {
        if (service.getSwaggerEndpoint() == null || service.getSwaggerEndpoint().isBlank()) {
            throw new IllegalStateException(
                    "Service with id " + service.getServiceId() + " has no swagger endpoint configured");
        }

        String url = service.getServiceBaseUrl() + service.getSwaggerEndpoint();

        return restClient.get()
                .uri(url)
                .retrieve()
                .body(JsonNode.class);
    }

    private List<SwaggerEndpointResponse> parseEndpoints(JsonNode swagger) {
        List<SwaggerEndpointResponse> result = new ArrayList<>();
        JsonNode paths = swagger.path("paths");

        paths.fieldNames().forEachRemaining(path -> {
            JsonNode pathItem = paths.get(path);
            pathItem.fieldNames().forEachRemaining(method -> {
                try {
                    HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
                    result.add(SwaggerEndpointResponse.builder()
                            .httpMethod(httpMethod)
                            .path(path)
                            .build());
                } catch (IllegalArgumentException ignored) {
                    // skip methods not in our enum (OPTIONS, HEAD, TRACE, etc.)
                }
            });
        });

        return result;
    }
}
