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
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

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

    static Stream<Arguments> passwordsUtf8() {
        return Stream.of(
                Arguments.of("ASCII, 72 bytes", "Aa1!" + "a".repeat(68), true),
                Arguments.of("ASCII, 73 bytes", "Aa1!" + "a".repeat(69), false),
                Arguments.of("Acentos, 72 bytes", "Aa1!" + "ñ".repeat(34), true),
                Arguments.of("Acentos, 73 bytes", "Aa1!" + "ñ".repeat(34) + "x", false),
                Arguments.of("Emoji, 72 bytes", "Aa1!" + "🙂".repeat(17), true),
                Arguments.of("Emoji, 76 bytes", "Aa1!" + "🙂".repeat(18), false));
    }

    @ParameterizedTest(name = "H11 - {0}")
    @MethodSource("passwordsUtf8")
    void limiteBcryptUtf8(String caso, String password, boolean permitido) {
        Set<ConstraintViolation<RegisterRequest>> errores =
                validar(new RegisterRequest("Ana", "Prueba", "ana@prueba.invalid", password));
        if (permitido) {
            assertThat(errores).isEmpty();
        } else {
            assertThat(errores).anyMatch(v -> v.getPropertyPath().toString().equals("password")
                    && v.getMessage().contains("72 bytes"));
        }
    }

    @Test
    @DisplayName("Contraseña ausente produce validación, no una excepción del validador UTF-8")
    void passwordAusente() {
        assertThat(validar(new RegisterRequest("Ana", "Prueba", "ana@prueba.invalid", null)))
                .anyMatch(v -> v.getPropertyPath().toString().equals("password"));
    }
}
