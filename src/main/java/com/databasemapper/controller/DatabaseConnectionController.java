package com.databasemapper.controller;

import com.databasemapper.dto.ConnectionRequest;
import com.databasemapper.dto.ConnectionResponse;
import com.databasemapper.dto.ErrorResponse;
import com.databasemapper.service.ConnectionService;
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
@RequestMapping("/api/v1/databases/{databaseId}/connections")
@RequiredArgsConstructor
public class DatabaseConnectionController {

    private final ConnectionService connectionService;

    @Operation(summary = "Get all connections for a database")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Connections returned"),
        @ApiResponse(responseCode = "404", description = "Database not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<List<ConnectionResponse>> getByDatabaseId(@PathVariable Long databaseId) {
        return ResponseEntity.ok(connectionService.getByDatabaseId(databaseId));
    }

    @Operation(summary = "Create a new connection for a database")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Connection created"),
        @ApiResponse(responseCode = "400", description = "Invalid request body or mode-specific fields missing",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Database not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ConnectionResponse> create(
            @PathVariable Long databaseId,
            @Valid @RequestBody ConnectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(connectionService.create(databaseId, request));
    }
}
