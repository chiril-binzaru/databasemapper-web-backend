package com.databasemapper.dto;

import com.databasemapper.model.enums.DatabaseType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DatabaseRequest {

    @NotNull
    private DatabaseType databaseType;

    @NotBlank
    @Size(max = 255)
    private String databaseHost;

    @NotBlank
    @Size(max = 255)
    private String databaseName;
}
