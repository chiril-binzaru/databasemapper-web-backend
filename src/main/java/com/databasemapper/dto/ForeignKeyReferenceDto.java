package com.databasemapper.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ForeignKeyReferenceDto {
    private String constraintName;
    private String referencedSchema;
    private String referencedTable;
    private String referencedColumn;
}
