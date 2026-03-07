package com.databasemapper.service;

import com.databasemapper.dto.ServiceRequest;
import com.databasemapper.dto.ServiceResponse;
import com.databasemapper.model.Database;
import com.databasemapper.repository.DatabaseRepository;
import com.databasemapper.repository.ServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceServiceImpl implements ServiceService {

    private final ServiceRepository serviceRepository;
    private final DatabaseRepository databaseRepository;

    @Override
    public ServiceResponse create(ServiceRequest request) {
        com.databasemapper.model.Service service = new com.databasemapper.model.Service();
        service.setServiceName(request.getServiceName());
        service.setServiceBaseUrl(request.getServiceBaseUrl());
        service.setSwaggerEndpoint(request.getSwaggerEndpoint());

        if (request.getDatabaseId() != null) {
            Database database = databaseRepository.findById(request.getDatabaseId())
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Database not found with id: " + request.getDatabaseId()));
            service.setDatabase(database);
        }

        com.databasemapper.model.Service saved = serviceRepository.save(service);

        return toResponse(saved);
    }

    @Override
    public List<ServiceResponse> getAll() {
        return serviceRepository.findAllByOrderByServiceNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    private ServiceResponse toResponse(com.databasemapper.model.Service service) {
        return ServiceResponse.builder()
                .serviceId(service.getServiceId())
                .databaseId(service.getDatabase() != null ? service.getDatabase().getDatabaseId() : null)
                .serviceName(service.getServiceName())
                .serviceBaseUrl(service.getServiceBaseUrl())
                .swaggerEndpoint(service.getSwaggerEndpoint())
                .build();
    }
}
