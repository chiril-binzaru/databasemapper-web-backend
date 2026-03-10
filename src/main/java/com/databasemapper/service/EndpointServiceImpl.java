package com.databasemapper.service;

import com.databasemapper.dto.EndpointRequest;
import com.databasemapper.dto.EndpointResponse;
import com.databasemapper.dto.ServiceEndpointsResponse;
import com.databasemapper.model.Endpoint;
import com.databasemapper.model.Service;
import com.databasemapper.repository.EndpointRepository;
import com.databasemapper.repository.ServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class EndpointServiceImpl implements EndpointService {

    private final EndpointRepository endpointRepository;
    private final ServiceRepository serviceRepository;

    @Override
    public ServiceEndpointsResponse create(EndpointRequest request) {
        Service service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Service not found with id: " + request.getServiceId()));

        Endpoint endpoint = new Endpoint();
        endpoint.setService(service);
        endpoint.setHttpMethod(request.getHttpMethod());
        endpoint.setEndpointPath(request.getEndpointPath());

        endpointRepository.save(endpoint);

        return ServiceEndpointsResponse.builder()
                .serviceId(service.getServiceId())
                .endpoints(endpointRepository.findAllByService_ServiceIdOrderByEndpointPathAscHttpMethodAsc(service.getServiceId())
                        .stream()
                        .map(this::toResponse)
                        .toList())
                .build();
    }

    private EndpointResponse toResponse(Endpoint endpoint) {
        return EndpointResponse.builder()
                .endpointId(endpoint.getEndpointId())
                .httpMethod(endpoint.getHttpMethod())
                .endpointPath(endpoint.getEndpointPath())
                .build();
    }
}
