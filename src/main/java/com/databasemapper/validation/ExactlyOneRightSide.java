package com.databasemapper.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ExactlyOneRightSideValidator.class)
public @interface ExactlyOneRightSide {
    String message() default "Exactly one of right, rightLiteral, or rightValues must be provided; " +
            "IN/NOT_IN requires rightValues, all other operators require right or rightLiteral";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
