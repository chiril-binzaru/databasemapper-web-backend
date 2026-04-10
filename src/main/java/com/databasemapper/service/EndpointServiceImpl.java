package com.databasemapper.service;

import com.databasemapper.dto.*;
import com.databasemapper.model.Endpoint;
import com.databasemapper.model.Service;
import com.databasemapper.model.enums.EndpointSyncStatus;
import com.databasemapper.model.enums.SyncStatus;
import com.databasemapper.repository.EndpointRepository;
import com.databasemapper.repository.MappingRepository;
import com.databasemapper.repository.ServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class EndpointServiceImpl implements EndpointService {

    private final EndpointRepository endpointRepository;
    private final ServiceRepository serviceRepository;
    private final MappingRepository mappingRepository;
    private final SwaggerService swaggerService;

    @Override
    public ServiceEndpointsResponse getByServiceId(Long serviceId) {
        requireService(serviceId);
        return toServiceEndpointsResponse(serviceId);
    }

    @Override
    public ServiceEndpointsResponse create(Long serviceId, EndpointRequest request) {
        Service service = requireService(serviceId);

        Endpoint endpoint = new Endpoint();
        endpoint.setService(service);
        endpoint.setHttpMethod(request.getHttpMethod());
        endpoint.setEndpointPath(request.getEndpointPath());

        endpointRepository.save(endpoint);

        return toServiceEndpointsResponse(serviceId);
    }

    @Override
    @Transactional
    public EndpointSyncResponse syncEndpoints(Long serviceId) {
        Service service = requireService(serviceId);

        List<SwaggerEndpointResponse> swaggerEndpoints = swaggerService.fetchSwaggerEndpoints(serviceId);
        List<Endpoint> dbEndpoints = endpointRepository
                .findAllByService_ServiceIdOrderByEndpointPathAscHttpMethodAsc(serviceId);

        Set<String> swaggerKeys = toKeys(swaggerEndpoints);
        Set<String> dbKeys = dbEndpoints.stream()
                .map(e -> e.getHttpMethod() + ":" + e.getEndpointPath())
                .collect(Collectors.toSet());

        if (swaggerKeys.equals(dbKeys)) {
            return EndpointSyncResponse.builder()
                    .syncStatus(SyncStatus.UNCHANGED)
                    .build();
        }

        if (dbEndpoints.isEmpty()) {
            List<EndpointSyncItemResponse> items = swaggerEndpoints.stream()
                    .map(se -> EndpointSyncItemResponse.builder()
                            .httpMethod(se.getHttpMethod())
                            .path(se.getPath())
                            .build())
                    .toList();

            return EndpointSyncResponse.builder()
                    .syncStatus(SyncStatus.TO_ADD_ALL)
                    .endpoints(items)
                    .build();
        }

        // Conflict — build merged list: UNCHANGED + TO_REMOVE first, then TO_ADD
        List<EndpointSyncItemResponse> items = new ArrayList<>();

        for (Endpoint dbEndpoint : dbEndpoints) {
            String key = dbEndpoint.getHttpMethod() + ":" + dbEndpoint.getEndpointPath();
            EndpointSyncStatus status = swaggerKeys.contains(key)
                    ? EndpointSyncStatus.UNCHANGED
                    : EndpointSyncStatus.TO_REMOVE;
            items.add(EndpointSyncItemResponse.builder()
                    .httpMethod(dbEndpoint.getHttpMethod())
                    .path(dbEndpoint.getEndpointPath())
                    .status(status)
                    .build());
        }

        for (SwaggerEndpointResponse se : swaggerEndpoints) {
            String key = se.getHttpMethod() + ":" + se.getPath();
            if (!dbKeys.contains(key)) {
                items.add(EndpointSyncItemResponse.builder()
                        .httpMethod(se.getHttpMethod())
                        .path(se.getPath())
                        .status(EndpointSyncStatus.TO_ADD)
                        .build());
            }
        }

        return EndpointSyncResponse.builder()
                .syncStatus(SyncStatus.CONFLICT)
                .endpoints(items)
                .build();
    }

    @Override
    @Transactional
    public ServiceEndpointsResponse replaceEndpoints(Long serviceId, List<EndpointReplaceRequest> endpoints) {
        Service service = requireService(serviceId);

        List<Long> endpointIds = endpointRepository
                .findAllByService_ServiceIdOrderByEndpointPathAscHttpMethodAsc(serviceId)
                .stream()
                .map(Endpoint::getEndpointId)
                .toList();

        if (!endpointIds.isEmpty()) {
            mappingRepository.deleteByEndpoint_EndpointIdIn(endpointIds);
        }

        endpointRepository.deleteByService_ServiceId(serviceId);

        List<Endpoint> newEndpoints = endpoints.stream()
                .map(req -> {
                    Endpoint e = new Endpoint();
                    e.setService(service);
                    e.setHttpMethod(req.getHttpMethod());
                    e.setEndpointPath(req.getPath());
                    return e;
                })
                .toList();
        endpointRepository.saveAll(newEndpoints);

        return toServiceEndpointsResponse(serviceId);
    }

    private ServiceEndpointsResponse toServiceEndpointsResponse(Long serviceId) {
        return ServiceEndpointsResponse.builder()
                .serviceId(serviceId)
                .endpoints(endpointRepository
                        .findAllByService_ServiceIdOrderByEndpointPathAscHttpMethodAsc(serviceId)
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

    private Service requireService(Long serviceId) {
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new EntityNotFoundException("Service not found with id: " + serviceId));
    }

    private Set<String> toKeys(List<SwaggerEndpointResponse> endpoints) {
        return endpoints.stream()
                .map(e -> e.getHttpMethod() + ":" + e.getPath())
                .collect(Collectors.toSet());
    }
}
