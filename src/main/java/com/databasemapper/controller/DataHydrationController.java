package com.databasemapper.controller;

import com.databasemapper.dto.DataFetchByNameRequest;
import com.databasemapper.dto.DataFetchRequest;
import com.databasemapper.dto.ErrorResponse;
import com.databasemapper.service.MappingHydrationService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/data")
@RequiredArgsConstructor
public class DataHydrationController {

    private final MappingHydrationService mappingHydrationService;

    @Operation(summary = "Fetch database data hydrated as the endpoint's model, looked up by endpoint ID")
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
    @PostMapping("/endpoints/{endpointId}")
    public ResponseEntity<JsonNode> fetchDataById(
            @PathVariable Long endpointId,
            @RequestBody DataFetchRequest request) {
        return ResponseEntity.ok(mappingHydrationService.fetchData(endpointId, request));
    }

    @Operation(summary = "Fetch database data hydrated as the endpoint's model, identified by service name + endpoint path + HTTP method")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Data returned as hydrated model JSON"),
        @ApiResponse(responseCode = "400", description = "Invalid or missing request fields",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Endpoint, mapping, or matching data not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Database connection or query failure",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<JsonNode> fetchDataByName(@Valid @RequestBody DataFetchByNameRequest request) {
        DataFetchRequest dataFetchRequest = new DataFetchRequest();
        dataFetchRequest.setFilters(request.getFilters());
        return ResponseEntity.ok(mappingHydrationService.fetchData(
                request.getServiceName(),
                request.getEndpointPath(),
                request.getHttpMethod(),
                dataFetchRequest));
    }
}
