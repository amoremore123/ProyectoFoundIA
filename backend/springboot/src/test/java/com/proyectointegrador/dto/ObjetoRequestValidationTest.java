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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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
    @DisplayName("H1 - El nombre admite 150 caracteres y rechaza 151 antes de guardar")
    void limiteNombre() {
        ObjetoRequest permitido = new ObjetoRequest(3L, "N".repeat(150), "Descripción", LocalDate.now(),
                TipoObjeto.PERDIDO, "Biblioteca", null, null);
        ObjetoRequest demasiadoLargo = new ObjetoRequest(3L, "N".repeat(151), "Descripción", LocalDate.now(),
                TipoObjeto.PERDIDO, "Biblioteca", null, null);
        assertThat(validar(permitido)).isEmpty();
        assertThat(validar(demasiadoLargo)).anyMatch(v -> v.getPropertyPath().toString().equals("nombre")
                && v.getMessage().contains("150"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("H3 - La ubicación no puede faltar ni estar en blanco")
    void ubicacionObligatoria(String ubicacion) {
        ObjetoRequest r = new ObjetoRequest(3L, "Mochila", "Descripción", LocalDate.now(),
                TipoObjeto.PERDIDO, ubicacion, null, null);
        assertThat(validar(r)).anyMatch(v -> v.getPropertyPath().toString().equals("ubicacion"));
    }

    @Test
    @DisplayName("H3 - La ubicación admite 255 caracteres y rechaza 256")
    void limiteUbicacion() {
        ObjetoRequest permitido = new ObjetoRequest(3L, "Mochila", "Descripción", LocalDate.now(),
                TipoObjeto.PERDIDO, "U".repeat(255), null, null);
        ObjetoRequest demasiadoLargo = new ObjetoRequest(3L, "Mochila", "Descripción", LocalDate.now(),
                TipoObjeto.PERDIDO, "U".repeat(256), null, null);
        assertThat(validar(permitido)).isEmpty();
        assertThat(validar(demasiadoLargo)).anyMatch(v -> v.getPropertyPath().toString().equals("ubicacion")
                && v.getMessage().contains("255"));
    }

    @Test
    @DisplayName("H3 - La dirección manual es válida sin coordenadas")
    void ubicacionManualSinCoordenadas() {
        ObjetoRequest r = new ObjetoRequest(3L, "Mochila", "Descripción", LocalDate.now(),
                TipoObjeto.PERDIDO, "Biblioteca central", null, null);
        assertThat(validar(r)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            "91, 0, latitud", "-91, 0, latitud", "0, 181, longitud", "0, -181, longitud",
            "90.0000001, 0, latitud", "-90.0000001, 0, latitud",
            "0, 180.0000001, longitud", "0, -180.0000001, longitud"
    })
    @DisplayName("H3 - Las coordenadas fuera de rango se rechazan antes de llegar a MySQL")
    void coordenadasFueraDeRango(BigDecimal latitud, BigDecimal longitud, String campo) {
        ObjetoRequest r = new ObjetoRequest(3L, "Mochila", "Descripción", LocalDate.now(),
                TipoObjeto.PERDIDO, "Biblioteca", latitud, longitud);
        assertThat(validar(r)).anyMatch(v -> v.getPropertyPath().toString().equals(campo));
    }

    @ParameterizedTest
    @CsvSource({"90, 180", "-90, -180", "0, 0", "19.43261, -99.1332"})
    @DisplayName("H3 - Los límites geográficos y las coordenadas interiores son válidos")
    void coordenadasPermitidas(BigDecimal latitud, BigDecimal longitud) {
        ObjetoRequest r = new ObjetoRequest(3L, "Mochila", "Descripción", LocalDate.now(),
                TipoObjeto.PERDIDO, "Biblioteca", latitud, longitud);
        assertThat(validar(r)).isEmpty();
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
