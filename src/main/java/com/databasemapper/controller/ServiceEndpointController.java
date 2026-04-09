package com.databasemapper.controller;

import com.databasemapper.dto.EndpointReplaceRequest;
import com.databasemapper.dto.EndpointRequest;
import com.databasemapper.dto.EndpointSyncResponse;
import com.databasemapper.dto.ServiceEndpointsResponse;
import com.databasemapper.service.EndpointService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/services/{serviceId}/endpoints")
@RequiredArgsConstructor
public class ServiceEndpointController {

    private final EndpointService endpointService;

    @Operation(summary = "Creates an endpoint entity for the specified service")
    @PostMapping
    public ResponseEntity<ServiceEndpointsResponse> create(
            @PathVariable Long serviceId,
            @Valid @RequestBody EndpointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(endpointService.create(serviceId, request));
    }

    @PostMapping("/sync")
    public ResponseEntity<EndpointSyncResponse> sync(@PathVariable Long serviceId) {
        return ResponseEntity.ok(endpointService.syncEndpoints(serviceId));
    }

    @PutMapping
    public ResponseEntity<ServiceEndpointsResponse> replace(
            @PathVariable Long serviceId,
            @Valid @RequestBody List<EndpointReplaceRequest> endpoints) {
        return ResponseEntity.ok(endpointService.replaceEndpoints(serviceId, endpoints));
    }
}
