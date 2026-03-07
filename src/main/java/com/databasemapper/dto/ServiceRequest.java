package com.databasemapper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ServiceRequest {

    private Long databaseId;

    @NotBlank
    @Size(max = 255)
    private String serviceName;

    @NotBlank
    @Size(max = 255)
    private String serviceBaseUrl;

    @Size(max = 255)
    private String swaggerEndpoint;
}
