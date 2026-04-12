package com.databasemapper.validation;

import com.databasemapper.dto.ConnectionRequest;
import com.databasemapper.model.enums.ConnectionMode;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ConnectionRequestValidator implements ConstraintValidator<ValidConnectionRequest, ConnectionRequest> {

    @Override
    public boolean isValid(ConnectionRequest request, ConstraintValidatorContext context) {
        if (request.getConnectionMode() == null) {
            return true; // @NotNull on the field handles this case
        }

        context.disableDefaultConstraintViolation();

        if (request.getConnectionMode() == ConnectionMode.PARAMETERS) {
            boolean valid = true;

            if (request.getPort() == null) {
                context.buildConstraintViolationWithTemplate("port is required for PARAMETERS mode")
                        .addPropertyNode("port")
                        .addConstraintViolation();
                valid = false;
            }
            if (request.getUsername() == null || request.getUsername().isBlank()) {
                context.buildConstraintViolationWithTemplate("username is required for PARAMETERS mode")
                        .addPropertyNode("username")
                        .addConstraintViolation();
                valid = false;
            }
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                context.buildConstraintViolationWithTemplate("password is required for PARAMETERS mode")
                        .addPropertyNode("password")
                        .addConstraintViolation();
                valid = false;
            }

            return valid;
        }

        if (request.getConnectionMode() == ConnectionMode.CONNECTION_STRING) {
            if (request.getConnectionString() == null || request.getConnectionString().isBlank()) {
                context.buildConstraintViolationWithTemplate("connectionString is required for CONNECTION_STRING mode")
                        .addPropertyNode("connectionString")
                        .addConstraintViolation();
                return false;
            }
        }

        return true;
    }
}
