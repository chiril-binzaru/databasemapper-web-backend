package com.databasemapper.dto;

import com.databasemapper.model.enums.ConditionOperator;
import com.databasemapper.validation.ExactlyOneRightSide;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@ExactlyOneRightSide
@Getter
@Setter
public class JoinConditionPairDto {

    @NotBlank
    private String left;

    @NotNull
    private ConditionOperator operator;

    private String right;
    private String rightLiteral;
    private List<String> rightValues;
}
