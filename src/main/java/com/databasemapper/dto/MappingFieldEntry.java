package com.databasemapper.dto;

import com.databasemapper.model.enums.FieldKind;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class MappingFieldEntry {

    @NotNull
    private FieldKind kind;

    @NotNull
    @Valid
    private FieldServiceInfo serviceInfo;

    // null for MODEL and LIST_OF_MODELS
    @Valid
    private FieldDatabaseInfo databaseInfo;

    // null for VALUE and LIST_OF_VALUES
    private List<MappingFieldEntry> fieldMappings;
}
