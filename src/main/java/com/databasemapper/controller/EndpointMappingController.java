package com.databasemapper.controller;

import com.databasemapper.dto.DataFetchRequest;
import com.databasemapper.dto.ErrorResponse;
import com.databasemapper.dto.MappingDto;
import com.databasemapper.service.EndpointMappingService;
import com.databasemapper.service.MappingHydrationService;
import com.fasterxml.jackson.databind.JsonNode;
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
@RequestMapping("/api/v1/endpoints/{endpointId}")
@RequiredArgsConstructor
public class EndpointMappingController {

    private final EndpointMappingService endpointMappingService;
    private final MappingHydrationService mappingHydrationService;

    @Operation(summary = "Get mapping data for an endpoint")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mapping data returned, or null if no mapping exists yet"),
        @ApiResponse(responseCode = "404", description = "Endpoint not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/mapping")
    public ResponseEntity<MappingDto> getMappingData(@PathVariable Long endpointId) {
        return ResponseEntity.ok(endpointMappingService.getMappingData(endpointId));
    }

    @Operation(summary = "Create mapping for an endpoint")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Mapping created"),
        @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Endpoint not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Mapping already exists for this endpoint",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/mapping")
    public ResponseEntity<MappingDto> saveMapping(
            @PathVariable Long endpointId,
            @Valid @RequestBody MappingDto request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(endpointMappingService.saveMapping(endpointId, request));
    }

    @Operation(summary = "Replace mapping for an endpoint")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mapping replaced"),
        @ApiResponse(responseCode = "400", description = "Invalid request body",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Endpoint or mapping not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/mapping")
    public ResponseEntity<MappingDto> replaceMapping(
            @PathVariable Long endpointId,
            @Valid @RequestBody MappingDto request) {
        return ResponseEntity.ok(endpointMappingService.replaceMapping(endpointId, request));
    }

    @Operation(summary = "Get the committed response model for an endpoint")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Response model returned, or null if not yet synced"),
        @ApiResponse(responseCode = "404", description = "Endpoint not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/responseModel")
    public ResponseEntity<JsonNode> getResponseModel(@PathVariable Long endpointId) {
        return ResponseEntity.ok(endpointMappingService.getResponseModel(endpointId));
    }

    @Operation(summary = "Fetch database data hydrated as the endpoint's model")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Data returned as hydrated model JSON"),
        @ApiResponse(responseCode = "400", description = "Invalid or missing filter fields",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Endpoint, mapping, or matching data not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Database connection or query failure",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/data")
    public ResponseEntity<JsonNode> fetchData(
            @PathVariable Long endpointId,
            @RequestBody DataFetchRequest request) {
        return ResponseEntity.ok(mappingHydrationService.fetchData(endpointId, request));
    }
}
