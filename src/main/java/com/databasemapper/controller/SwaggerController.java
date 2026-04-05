package com.databasemapper.controller;

import com.databasemapper.service.SwaggerService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/services/{serviceId}/swagger")
@RequiredArgsConstructor
public class SwaggerController {

    private final SwaggerService swaggerService;

    @GetMapping
    public ResponseEntity<JsonNode> fetchSwagger(@PathVariable Long serviceId) {
        return ResponseEntity.ok(swaggerService.fetchAndSave(serviceId));
    }

    @PostMapping("/commit")
    public ResponseEntity<JsonNode> commitSwagger(@PathVariable Long serviceId) {
        return ResponseEntity.ok(swaggerService.commit(serviceId));
    }
}
