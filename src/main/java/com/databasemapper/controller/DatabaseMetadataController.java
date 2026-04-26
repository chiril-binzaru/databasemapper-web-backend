package com.databasemapper.controller;

import com.databasemapper.dto.DbColumnResponse;
import com.databasemapper.dto.ErrorResponse;
import com.databasemapper.service.DatabaseMetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/databases/{databaseId}")
@RequiredArgsConstructor
public class DatabaseMetadataController {

    private final DatabaseMetadataService databaseMetadataService;

    @Operation(summary = "List all schemas in the target database")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Schemas returned"),
        @ApiResponse(responseCode = "404", description = "Database not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "No active connection or connection failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/schemas")
    public ResponseEntity<List<String>> getSchemas(@PathVariable Long databaseId) {
        return ResponseEntity.ok(databaseMetadataService.getSchemas(databaseId));
    }

    @Operation(summary = "List all tables in a schema of the target database")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tables returned"),
        @ApiResponse(responseCode = "404", description = "Database not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "No active connection or connection failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/schemas/{schemaName}/tables")
    public ResponseEntity<List<String>> getTables(
            @PathVariable Long databaseId,
            @PathVariable String schemaName) {
        return ResponseEntity.ok(databaseMetadataService.getTables(databaseId, schemaName));
    }

    @Operation(summary = "List all columns of a table in the target database")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Columns returned"),
        @ApiResponse(responseCode = "404", description = "Database not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "No active connection or connection failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/schemas/{schemaName}/tables/{tableName}/columns")
    public ResponseEntity<List<DbColumnResponse>> getColumns(
            @PathVariable Long databaseId,
            @PathVariable String schemaName,
            @PathVariable String tableName) {
        return ResponseEntity.ok(databaseMetadataService.getColumns(databaseId, schemaName, tableName));
    }
}
