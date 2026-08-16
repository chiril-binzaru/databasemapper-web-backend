package com.databasemapper.controller;

import com.databasemapper.dto.ErrorResponse;
import com.databasemapper.dto.SwaggerEndpointResponse;
import com.databasemapper.service.SwaggerService;
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

    @Operation(summary = "Fetch and parse endpoints from the service's live Swagger spec")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Endpoints parsed and returned"),
        @ApiResponse(responseCode = "404", description = "Service not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "422", description = "Service has no Swagger endpoint configured",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
        @ApiResponse(responseCode = "500", description = "Unexpected error",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/endpoints")
    public ResponseEntity<List<SwaggerEndpointResponse>> fetchEndpoints(@PathVariable Long serviceId) {
        return ResponseEntity.ok(swaggerService.fetchSwaggerEndpoints(serviceId));
    }
}
