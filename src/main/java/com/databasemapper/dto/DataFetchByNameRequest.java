package com.databasemapper.dto;

import com.databasemapper.model.HttpMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class DataFetchByNameRequest {

    @NotBlank
    private String serviceName;

    @NotBlank
    private String endpointPath;

    @NotNull
    private HttpMethod httpMethod;

    private Map<String, Object> filters;
}
