package com.databasemapper.dto;

import com.databasemapper.model.enums.FieldKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MappingFieldEntry {

    @NotBlank
    private String modelField;

    @NotNull
    private FieldKind kind;

    // OpenAPI type (e.g. string, integer, boolean, number) — null for MODEL and LIST_OF_MODELS
    private String type;

    // OpenAPI format (e.g. date-time, int64) — optional, null when not specified
    private String format;

    // present for VALUE and LIST_OF_VALUES; null for MODEL and LIST_OF_MODELS
    private String columnPath;

    // present for VALUE and LIST_OF_VALUES; null for MODEL and LIST_OF_MODELS
    private Boolean isPrimaryKey;

    // present for LIST_OF_VALUES, MODEL, LIST_OF_MODELS; null for VALUE
    private JoinConditionDto joinCondition;

    // present for MODEL and LIST_OF_MODELS; null for VALUE and LIST_OF_VALUES
    private String modelName;

    // present for MODEL and LIST_OF_MODELS; null for VALUE and LIST_OF_VALUES
    private List<MappingFieldEntry> fieldMappings;
}
