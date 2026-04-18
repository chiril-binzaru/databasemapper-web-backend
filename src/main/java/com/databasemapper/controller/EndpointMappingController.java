package com.databasemapper.controller;

import com.databasemapper.dto.ErrorResponse;
import com.databasemapper.service.EndpointMappingService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/endpoints/{endpointId}")
@RequiredArgsConstructor
public class EndpointMappingController {

    private final EndpointMappingService endpointMappingService;

    @Operation(summary = "Get mapping data for an endpoint")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Mapping data returned, or null if no mapping exists yet"),
        @ApiResponse(responseCode = "404", description = "Endpoint not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/mapping")
    public ResponseEntity<JsonNode> getMappingData(@PathVariable Long endpointId) {
        return ResponseEntity.ok(endpointMappingService.getMappingData(endpointId));
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
}
