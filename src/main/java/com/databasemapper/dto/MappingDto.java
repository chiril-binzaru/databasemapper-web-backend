package com.databasemapper.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MappingDto {

    @NotBlank
    private String modelName;

    @NotNull
    @Valid
    private List<MappingFieldEntry> fieldMappings;
}
