package com.databasemapper.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DbColumnResponse {
    private String name;
    private String dataType;
    private boolean nullable;
    private int ordinalPosition;
    private String columnDefault;
    private boolean primaryKey;
    private List<ForeignKeyReferenceDto> foreignKeys;
}
