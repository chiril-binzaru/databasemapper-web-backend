package com.databasemapper.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FieldDatabaseInfo {

    // null for unmapped fields
    private String columnPath;

    // DB column type as reported by the database (e.g. bigint, varchar, timestamp)
    private String columnType;

    // null for LIST_OF_VALUES
    private Boolean primaryKey;
}
