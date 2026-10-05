package com.proyectointegrador.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("H11 - Validaciones del formulario de registro")
class RegisterRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void init() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    private Set<ConstraintViolation<RegisterRequest>> validar(RegisterRequest r) {
        return validator.validate(r);
    }

    @Test
    @DisplayName("Datos válidos no generan errores")
    void datosValidos() {
        assertThat(validar(new RegisterRequest("María José", "Núñez", "maria@foundia.dev", "Segura123!"))).isEmpty();
    }

    @ParameterizedTest(name = "Contraseña débil: \"{0}\"")
    @ValueSource(strings = {"Corta1!", "sinmayuscula1!", "SINMINUSCULA1!", "SinNumero!!", "SinSimbolo123", ""})
    void passwordDebil(String password) {
        Set<ConstraintViolation<RegisterRequest>> errores =
                validar(new RegisterRequest("Ana", "Pérez", "ana@foundia.dev", password));
        assertThat(errores).anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }

    @Test
    @DisplayName("Correo con formato inválido")
    void correoInvalido() {
        assertThat(validar(new RegisterRequest("Ana", "Pérez", "ana-sin-arroba", "Segura123!")))
                .anyMatch(v -> v.getPropertyPath().toString().equals("correo"));
    }

    @Test
    @DisplayName("Nombre y apellido obligatorios y solo letras")
    void nombreYApellido() {
        assertThat(validar(new RegisterRequest("", "Pérez", "ana@foundia.dev", "Segura123!")))
                .anyMatch(v -> v.getPropertyPath().toString().equals("nombre"));
        assertThat(validar(new RegisterRequest("Ana", "P3rez", "ana@foundia.dev", "Segura123!")))
                .anyMatch(v -> v.getPropertyPath().toString().equals("apellido"));
    }
}
