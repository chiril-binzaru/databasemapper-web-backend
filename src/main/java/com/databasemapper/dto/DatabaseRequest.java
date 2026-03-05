package com.databasemapper.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DatabaseRequest {

    @NotBlank
    @Size(max = 50)
    private String databaseType;

    @NotBlank
    @Size(max = 255)
    private String databaseHost;

    @NotBlank
    @Size(max = 255)
    private String databaseName;
}
