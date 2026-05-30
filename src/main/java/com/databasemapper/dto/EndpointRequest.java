package com.databasemapper.dto;

import com.databasemapper.model.HttpMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EndpointRequest {

    @NotNull
    private HttpMethod httpMethod;

    @NotBlank
    @Size(max = 255)
    private String path;
}
