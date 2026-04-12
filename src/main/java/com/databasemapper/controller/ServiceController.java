package com.databasemapper.controller;

import com.databasemapper.dto.ErrorResponse;
import com.databasemapper.dto.ServiceRequest;
import com.databasemapper.dto.ServiceResponse;
import com.databasemapper.service.ServiceService;
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
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceController {

    private final ServiceService serviceService;

    @Operation(summary = "Get all services")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of services returned"),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<ServiceResponse>> getAll() {
        return ResponseEntity.ok(serviceService.getAll());
    }

    @Operation(summary = "Create a new service")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Service created"),
        @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Database not found for the provided databaseId",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<List<ServiceResponse>> create(@Valid @RequestBody ServiceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceService.create(request));
    }

    @Operation(summary = "Delete a service by ID")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Service deleted"),
        @ApiResponse(responseCode = "404", description = "Service not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{serviceId}")
    public ResponseEntity<Void> delete(@PathVariable Long serviceId) {
        serviceService.delete(serviceId);
        return ResponseEntity.noContent().build();
    }
}
