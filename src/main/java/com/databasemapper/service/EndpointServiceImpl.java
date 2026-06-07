package com.databasemapper.service;

import com.databasemapper.dto.*;
import com.databasemapper.model.Endpoint;
import com.databasemapper.model.Service;
import com.databasemapper.model.enums.EndpointSyncStatus;
import com.databasemapper.model.enums.SyncStatus;
import com.databasemapper.repository.EndpointRepository;
import com.databasemapper.repository.MappingRepository;
import com.databasemapper.repository.ServiceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        endpoint.setEndpointPath(request.getPath());

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
                    .syncStatus(SyncStatus.U)
                    .build();
        }

        if (dbKeys.isEmpty()) {
            List<EndpointSyncItemResponse> items = swaggerEndpoints.stream()
                    .map(se -> EndpointSyncItemResponse.builder()
                            .httpMethod(se.getHttpMethod())
                            .path(se.getPath())
                            .status(EndpointSyncStatus.A)
                            .build())
                    .toList();
            return EndpointSyncResponse.builder()
                    .syncStatus(SyncStatus.A)
                    .endpoints(items)
                    .build();
        }

        if (swaggerKeys.isEmpty()) {
            List<EndpointSyncItemResponse> items = dbEndpoints.stream()
                    .map(e -> EndpointSyncItemResponse.builder()
                            .httpMethod(e.getHttpMethod())
                            .path(e.getEndpointPath())
                            .status(EndpointSyncStatus.R)
                            .build())
                    .toList();
            return EndpointSyncResponse.builder()
                    .syncStatus(SyncStatus.R)
                    .endpoints(items)
                    .build();
        }

        boolean hasAdds = !swaggerKeys.stream().allMatch(dbKeys::contains);
        boolean hasRemoves = !dbKeys.stream().allMatch(swaggerKeys::contains);

        SyncStatus syncStatus = (hasAdds && hasRemoves) ? SyncStatus.AUR
                : hasAdds ? SyncStatus.AU
                : SyncStatus.UR;

        List<EndpointSyncItemResponse> items = new ArrayList<>();
        for (Endpoint dbEndpoint : dbEndpoints) {
            String key = dbEndpoint.getHttpMethod() + ":" + dbEndpoint.getEndpointPath();
            EndpointSyncStatus status = swaggerKeys.contains(key)
                    ? EndpointSyncStatus.U
                    : EndpointSyncStatus.R;
            items.add(EndpointSyncItemResponse.builder()
                    .httpMethod(dbEndpoint.getHttpMethod())
                    .path(dbEndpoint.getEndpointPath())
                    .status(status)
                    .build());
        }
        for (SwaggerEndpointResponse se : swaggerEndpoints) {
            if (!dbKeys.contains(se.getHttpMethod() + ":" + se.getPath())) {
                items.add(EndpointSyncItemResponse.builder()
                        .httpMethod(se.getHttpMethod())
                        .path(se.getPath())
                        .status(EndpointSyncStatus.A)
                        .build());
            }
        }

        return EndpointSyncResponse.builder()
                .syncStatus(syncStatus)
                .endpoints(items)
                .build();
    }

    @Override
    @Transactional
    public ServiceEndpointsResponse replaceEndpoints(Long serviceId, List<EndpointReplaceRequest> endpoints) {
        Service service = requireService(serviceId);

        Map<String, SwaggerService.EndpointModelResult> responseModels = swaggerService.fetchResponseModels(serviceId);

        Map<Long, Endpoint> dbEndpointsById = endpointRepository
                .findAllByService_ServiceIdOrderByEndpointPathAscHttpMethodAsc(serviceId)
                .stream()
                .collect(Collectors.toMap(Endpoint::getEndpointId, e -> e));

        Set<Long> requestIds = endpoints.stream()
                .map(EndpointReplaceRequest::getEndpointId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> foreignIds = requestIds.stream()
                .filter(id -> !dbEndpointsById.containsKey(id))
                .collect(Collectors.toSet());
        if (!foreignIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Endpoint IDs do not belong to service " + serviceId + ": " + foreignIds);
        }

        for (EndpointReplaceRequest req : endpoints) {
            if (req.getEndpointId() != null) continue;
            dbEndpointsById.values().stream()
                    .filter(e -> e.getHttpMethod() == req.getHttpMethod()
                              && e.getEndpointPath().equals(req.getPath()))
                    .findFirst()
                    .ifPresent(e -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                req.getHttpMethod().name() + " endpoint '" + req.getPath() +
                                "' is requested to be added but already exists for this service. " +
                                "If you intended to update an existing endpoint, send its actual id instead of null.");
                    });
        }

        Set<Long> idsToDelete = dbEndpointsById.keySet().stream()
                .filter(id -> !requestIds.contains(id))
                .collect(Collectors.toSet());
        if (!idsToDelete.isEmpty()) {
            mappingRepository.deleteByEndpoint_EndpointIdIn(new ArrayList<>(idsToDelete));
            endpointRepository.deleteAllByIdInBatch(idsToDelete);
        }

        List<Endpoint> toSave = new ArrayList<>();
        for (EndpointReplaceRequest req : endpoints) {
            SwaggerService.EndpointModelResult modelResult = responseModels.get(req.getHttpMethod() + ":" + req.getPath());
            JsonNode responseModel = modelResult != null ? modelResult.responseModel() : null;
            boolean isList = modelResult != null && modelResult.isList();
            if (req.getEndpointId() != null) {
                Endpoint existing = dbEndpointsById.get(req.getEndpointId());
                existing.setHttpMethod(req.getHttpMethod());
                existing.setEndpointPath(req.getPath());
                existing.setResponseModel(responseModel);
                existing.setListResponse(isList);
                toSave.add(existing);
            } else {
                Endpoint newEndpoint = new Endpoint();
                newEndpoint.setService(service);
                newEndpoint.setHttpMethod(req.getHttpMethod());
                newEndpoint.setEndpointPath(req.getPath());
                newEndpoint.setResponseModel(responseModel);
                newEndpoint.setListResponse(isList);
                toSave.add(newEndpoint);
            }
        }

        toSave.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getHttpMethod().name() + " " + e.getEndpointPath(),
                        Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .findFirst()
                .ifPresent(entry -> {
                    String[] parts = entry.getKey().split(" ", 2);
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            parts[0] + " endpoint '" + parts[1] + "' is requested to be added but already exists" +
                            " for this service. If you intended to update an existing endpoint, send its actual id instead of null.");
                });

        endpointRepository.saveAll(toSave);

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
                .path(endpoint.getEndpointPath())
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
