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
@RequestMapping("/api/v1/databases")
@RequiredArgsConstructor
public class DatabaseController {

    private final DatabaseService databaseService;

    @PostMapping
    public ResponseEntity<DatabaseResponse> create(@Valid @RequestBody DatabaseRequest request) {
        DatabaseResponse response = databaseService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
