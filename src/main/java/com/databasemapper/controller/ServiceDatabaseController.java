package com.databasemapper.controller;

import com.databasemapper.dto.DatabaseRequest;
import com.databasemapper.dto.DatabaseResponse;
import com.databasemapper.service.DatabaseService;
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

    @GetMapping
    public ResponseEntity<DatabaseResponse> getByServiceId(@PathVariable Long serviceId) {
        return ResponseEntity.ok(databaseService.getByServiceId(serviceId));
    }

    @PostMapping
    public ResponseEntity<DatabaseResponse> create(
            @PathVariable Long serviceId,
            @Valid @RequestBody DatabaseRequest request) {
        DatabaseResponse response = databaseService.create(serviceId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
