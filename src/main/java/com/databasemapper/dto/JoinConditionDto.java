package com.databasemapper.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class JoinConditionDto {

    private String parentColumn;
    private String childColumn;
}
