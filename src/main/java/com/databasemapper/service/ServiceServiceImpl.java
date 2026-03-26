package com.databasemapper.service;

import com.databasemapper.dto.ServiceRequest;
import com.databasemapper.dto.ServiceResponse;
import com.databasemapper.model.Database;
import com.databasemapper.repository.DatabaseRepository;
import com.databasemapper.repository.EndpointRepository;
import com.databasemapper.repository.MappingRepository;
import com.databasemapper.repository.ServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceServiceImpl implements ServiceService {

    private final ServiceRepository serviceRepository;
    private final DatabaseRepository databaseRepository;
    private final MappingRepository mappingRepository;
    private final EndpointRepository endpointRepository;

    @Override
    public List<ServiceResponse> create(ServiceRequest request) {
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

        serviceRepository.save(service);

        return getAll();
    }

    @Override
    public List<ServiceResponse> getAll() {
        return serviceRepository.findAllByOrderByServiceNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public void delete(Long serviceId) {
        if (!serviceRepository.existsById(serviceId)) {
            throw new EntityNotFoundException("Service not found with id: " + serviceId);
        }

        List<Long> endpointIds = endpointRepository
                .findAllByService_ServiceIdOrderByEndpointPathAscHttpMethodAsc(serviceId)
                .stream()
                .map(endpoint -> endpoint.getEndpointId())
                .toList();

        if (!endpointIds.isEmpty()) {
            mappingRepository.deleteByEndpoint_EndpointIdIn(endpointIds);
        }

        endpointRepository.deleteByService_ServiceId(serviceId);
        serviceRepository.deleteById(serviceId);
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
