package com.databasemapper.service;

import com.databasemapper.repository.ServiceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class SwaggerServiceImpl implements SwaggerService {

    private final ServiceRepository serviceRepository;
    private final RestClient restClient;

    @Override
    @Transactional
    public JsonNode fetchAndSave(Long serviceId) {
        com.databasemapper.model.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("Service not found with id: " + serviceId));

        if (service.getSwaggerEndpoint() == null || service.getSwaggerEndpoint().isBlank()) {
            throw new IllegalStateException("Service with id " + serviceId + " has no swagger endpoint configured");
        }

        String url = service.getServiceBaseUrl() + service.getSwaggerEndpoint();

        JsonNode swagger = restClient.get()
                .uri(url)
                .retrieve()
                .body(JsonNode.class);

        service.setLastFetchedSwagger(swagger);
        serviceRepository.save(service);

        return swagger;
    }

    @Override
    @Transactional
    public JsonNode commit(Long serviceId) {
        com.databasemapper.model.Service service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("Service not found with id: " + serviceId));

        if (service.getLastFetchedSwagger() == null) {
            throw new IllegalStateException("No fetched swagger to commit for service with id: " + serviceId);
        }

        service.setCommittedSwagger(service.getLastFetchedSwagger());
        serviceRepository.save(service);

        return service.getCommittedSwagger();
    }
}
