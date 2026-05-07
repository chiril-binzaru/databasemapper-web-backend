package com.databasemapper.dto;

import jakarta.validation.Valid;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class JoinEntryDto {

    private String left;
    private String right;

    @Valid
    private List<JoinConditionPairDto> additionalJoinConditions;
}
