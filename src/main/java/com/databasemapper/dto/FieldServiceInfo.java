package com.databasemapper.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FieldServiceInfo {

    @NotBlank
    private String modelField;

    // OpenAPI type (e.g. string, integer, boolean, number) — null for MODEL and LIST_OF_MODELS
    private String type;

    // OpenAPI format (e.g. date-time, int64) — null when not specified
    private String format;

    // null for VALUE and LIST_OF_VALUES
    private String modelName;
}
