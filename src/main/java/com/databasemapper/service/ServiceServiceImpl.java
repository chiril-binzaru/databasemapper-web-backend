package com.databasemapper.service;

import com.databasemapper.dto.ServiceRequest;
import com.databasemapper.dto.ServiceResponse;
import com.databasemapper.model.Database;
import com.databasemapper.repository.DatabaseRepository;
import com.databasemapper.repository.ServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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

        return ServiceResponse.builder()
                .serviceId(saved.getServiceId())
                .databaseId(saved.getDatabase() != null ? saved.getDatabase().getDatabaseId() : null)
                .serviceName(saved.getServiceName())
                .serviceBaseUrl(saved.getServiceBaseUrl())
                .swaggerEndpoint(saved.getSwaggerEndpoint())
                .build();
    }
}
