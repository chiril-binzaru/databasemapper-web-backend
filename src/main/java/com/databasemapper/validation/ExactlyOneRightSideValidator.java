package com.databasemapper.validation;

import com.databasemapper.dto.JoinConditionPairDto;
import com.databasemapper.model.enums.ConditionOperator;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ExactlyOneRightSideValidator implements ConstraintValidator<ExactlyOneRightSide, JoinConditionPairDto> {

    @Override
    public boolean isValid(JoinConditionPairDto dto, ConstraintValidatorContext context) {
        if (dto == null) return true;

        boolean hasRight = dto.getRight() != null;
        boolean hasLiteral = dto.getRightLiteral() != null;
        boolean hasValues = dto.getRightValues() != null && !dto.getRightValues().isEmpty();

        int count = (hasRight ? 1 : 0) + (hasLiteral ? 1 : 0) + (hasValues ? 1 : 0);
        if (count != 1) return false;

        ConditionOperator op = dto.getOperator();
        if (op == null) return true; // operator nullness handled by @NotNull on the field

        boolean isMultiValue = op == ConditionOperator.IN || op == ConditionOperator.NOT_IN;
        return isMultiValue ? hasValues : !hasValues;
    }
}
