package com.databasemapper.controller;

import com.databasemapper.dto.DatabaseRequest;
import com.databasemapper.dto.DatabaseResponse;
import com.databasemapper.dto.ErrorResponse;
import com.databasemapper.service.DatabaseService;
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

@RestController
@RequestMapping("/api/v1/services/{serviceId}/database")
@RequiredArgsConstructor
public class ServiceDatabaseController {

    private final DatabaseService databaseService;

    @Operation(summary = "Get the database assigned to a service")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Database found"),
        @ApiResponse(responseCode = "404", description = "Service not found or no database assigned",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<DatabaseResponse> getByServiceId(@PathVariable Long serviceId) {
        return ResponseEntity.ok(databaseService.getByServiceId(serviceId));
    }

    @Operation(summary = "Assign a new database to a service")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Database created and assigned"),
        @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Service not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Service already has a database assigned",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<DatabaseResponse> create(
            @PathVariable Long serviceId,
            @Valid @RequestBody DatabaseRequest request) {
        DatabaseResponse response = databaseService.create(serviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Update the database assigned to a service")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Database updated"),
        @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Service not found or no database assigned",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping
    public ResponseEntity<DatabaseResponse> update(
            @PathVariable Long serviceId,
            @Valid @RequestBody DatabaseRequest request) {
        return ResponseEntity.ok(databaseService.update(serviceId, request));
    }
}
