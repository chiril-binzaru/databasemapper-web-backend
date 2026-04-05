package com.databasemapper.controller;

import com.databasemapper.dto.EndpointReplaceRequest;
import com.databasemapper.dto.EndpointSyncResponse;
import com.databasemapper.dto.ServiceEndpointsResponse;
import com.databasemapper.service.EndpointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/services/{serviceId}/endpoints")
@RequiredArgsConstructor
public class ServiceEndpointController {

    private final EndpointService endpointService;

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
