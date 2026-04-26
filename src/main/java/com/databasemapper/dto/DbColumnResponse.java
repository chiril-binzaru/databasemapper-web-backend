package com.databasemapper.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DbColumnResponse {
    private String name;
    private String dataType;
    private boolean nullable;
    private int ordinalPosition;
    private String columnDefault;
}
