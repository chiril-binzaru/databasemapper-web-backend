package com.databasemapper.controller;

import com.databasemapper.dto.EndpointRequest;
import com.databasemapper.dto.ServiceEndpointsResponse;
import com.databasemapper.service.EndpointService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/endpoints")
@RequiredArgsConstructor
public class EndpointController {

    private final EndpointService endpointService;

    @PostMapping
    public ResponseEntity<ServiceEndpointsResponse> create(@Valid @RequestBody EndpointRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(endpointService.create(request));
    }
}
