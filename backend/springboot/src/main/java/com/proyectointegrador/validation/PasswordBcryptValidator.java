package com.proyectointegrador.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;

public class PasswordBcryptValidator implements ConstraintValidator<PasswordBcrypt, String> {
    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        // BCrypt limita bytes, no caracteres; @NotBlank valida los valores ausentes.
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
