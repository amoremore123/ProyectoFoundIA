package com.proyectointegrador.dto;

import com.proyectointegrador.entity.TipoObjeto;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("H1 - Validaciones del formulario de publicación")
class ObjetoRequestValidationTest {

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

    private ObjetoRequest valido() {
        return new ObjetoRequest(
                3L,
                "Mochila negra",
                "Mochila con libros de cálculo",
                LocalDate.of(2026, 10, 2),
                TipoObjeto.ENCONTRADO,
                "Comedor principal",
                new BigDecimal("19.4326100"),
                new BigDecimal("-99.1332000"));
    }

    private Set<ConstraintViolation<ObjetoRequest>> validar(ObjetoRequest r) {
        return validator.validate(r);
    }

    @Test
    @DisplayName("Datos válidos no generan errores")
    void datosValidos() {
        assertThat(validar(valido())).isEmpty();
    }

    @Test
    @DisplayName("Nombre obligatorio")
    void nombreObligatorio() {
        ObjetoRequest r = new ObjetoRequest(3L, "   ", "Descripción", LocalDate.of(2026, 10, 2),
                TipoObjeto.ENCONTRADO, "Comedor", null, null);
        assertThat(validar(r)).anyMatch(v -> v.getPropertyPath().toString().equals("nombre"));
    }

    @Test
    @DisplayName("Descripción obligatoria")
    void descripcionObligatoria() {
        ObjetoRequest r = new ObjetoRequest(3L, "Mochila", "", LocalDate.of(2026, 10, 2),
                TipoObjeto.ENCONTRADO, "Comedor", null, null);
        assertThat(validar(r)).anyMatch(v -> v.getPropertyPath().toString().equals("descripcion"));
    }

    @Test
    @DisplayName("Categoría obligatoria")
    void categoriaObligatoria() {
        ObjetoRequest r = new ObjetoRequest(null, "Mochila", "Descripción", LocalDate.of(2026, 10, 2),
                TipoObjeto.ENCONTRADO, "Comedor", null, null);
        assertThat(validar(r)).anyMatch(v -> v.getPropertyPath().toString().equals("categoriaId"));
    }

    @Test
    @DisplayName("Fecha obligatoria")
    void fechaObligatoria() {
        ObjetoRequest r = new ObjetoRequest(3L, "Mochila", "Descripción", null,
                TipoObjeto.ENCONTRADO, "Comedor", null, null);
        assertThat(validar(r)).anyMatch(v -> v.getPropertyPath().toString().equals("fechaObjeto"));
    }

    @Test
    @DisplayName("H4 - La fecha no puede ser futura")
    void fechaFutura() {
        ObjetoRequest r = new ObjetoRequest(3L, "Mochila", "Descripción", LocalDate.now().plusDays(1),
                TipoObjeto.ENCONTRADO, "Comedor", null, null);
        assertThat(validar(r)).anyMatch(v ->
                v.getPropertyPath().toString().equals("fechaObjeto")
                        && v.getMessage().contains("futura"));
    }

    @Test
    @DisplayName("H4 - La fecha de hoy sí se acepta")
    void fechaDeHoy() {
        ObjetoRequest r = new ObjetoRequest(3L, "Mochila", "Descripción", LocalDate.now(),
                TipoObjeto.ENCONTRADO, "Comedor", null, null);
        assertThat(validar(r)).isEmpty();
    }

    @Test
    @DisplayName("Tipo obligatorio")
    void tipoObligatorio() {
        ObjetoRequest r = new ObjetoRequest(3L, "Mochila", "Descripción", LocalDate.of(2026, 10, 2),
                null, "Comedor", null, null);
        assertThat(validar(r)).anyMatch(v -> v.getPropertyPath().toString().equals("tipo"));
    }
}
