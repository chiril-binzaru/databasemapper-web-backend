package com.databasemapper.controller;

import com.databasemapper.dto.EndpointReplaceRequest;
import com.databasemapper.dto.EndpointRequest;
import com.databasemapper.dto.EndpointSyncResponse;
import com.databasemapper.dto.ErrorResponse;
import com.databasemapper.dto.ServiceEndpointsResponse;
import com.databasemapper.service.EndpointService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

    @Operation(summary = "Returns all endpoint entities for the specified service")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Endpoints returned"),
        @ApiResponse(responseCode = "404", description = "Service not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ServiceEndpointsResponse> getByServiceId(@PathVariable Long serviceId) {
        return ResponseEntity.ok(endpointService.getByServiceId(serviceId));
    }

    @Operation(summary = "Creates an endpoint entity for the specified service")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Endpoint created"),
        @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Service not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ServiceEndpointsResponse> create(
            @PathVariable Long serviceId,
            @Valid @RequestBody EndpointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(endpointService.create(serviceId, request));
    }

    @Operation(summary = "Sync endpoints from the service's committed Swagger spec")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Sync result returned"),
        @ApiResponse(responseCode = "404", description = "Service not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Service has no Swagger endpoint configured",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/sync")
    public ResponseEntity<EndpointSyncResponse> sync(@PathVariable Long serviceId) {
        return ResponseEntity.ok(endpointService.syncEndpoints(serviceId));
    }

    @Operation(summary = "Replace all endpoints for the specified service")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Endpoints replaced"),
        @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Service not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Service has no Swagger endpoint configured",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping
    public ResponseEntity<ServiceEndpointsResponse> replace(
            @PathVariable Long serviceId,
            @Valid @RequestBody List<EndpointReplaceRequest> endpoints) {
        return ResponseEntity.ok(endpointService.replaceEndpoints(serviceId, endpoints));
    }
}
