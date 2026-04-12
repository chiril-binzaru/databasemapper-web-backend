package com.databasemapper.controller;

import com.databasemapper.dto.ErrorResponse;
import com.databasemapper.dto.SwaggerEndpointResponse;
import com.databasemapper.service.SwaggerService;
import com.fasterxml.jackson.databind.JsonNode;
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
@RequestMapping("/api/v1/services/{serviceId}/swagger")
@RequiredArgsConstructor
public class SwaggerController {

    private final SwaggerService swaggerService;

    @Operation(summary = "Fetch and save the latest Swagger spec from the service")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Swagger spec fetched and saved"),
        @ApiResponse(responseCode = "404", description = "Service not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error or no Swagger endpoint configured",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<JsonNode> fetchSwagger(@PathVariable Long serviceId) {
        return ResponseEntity.ok(swaggerService.fetchAndSave(serviceId));
    }

    @Operation(summary = "Commit the last fetched Swagger spec as the stable snapshot")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Swagger spec committed"),
        @ApiResponse(responseCode = "404", description = "Service not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error or no fetched Swagger to commit",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/commit")
    public ResponseEntity<JsonNode> commitSwagger(@PathVariable Long serviceId) {
        return ResponseEntity.ok(swaggerService.commit(serviceId));
    }

    @Operation(summary = "Fetch and parse endpoints from the service's live Swagger spec")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Endpoints parsed and returned"),
        @ApiResponse(responseCode = "404", description = "Service not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error or no Swagger endpoint configured",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/endpoints")
    public ResponseEntity<List<SwaggerEndpointResponse>> fetchEndpoints(@PathVariable Long serviceId) {
        return ResponseEntity.ok(swaggerService.fetchSwaggerEndpoints(serviceId));
    }
}
