package com.proyectointegrador.dto;

import com.proyectointegrador.entity.TipoObjeto;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ObjetoRequest(
        @NotNull(message = "es obligatorio") Long categoriaId,
        @NotBlank(message = "es obligatorio")
        @Size(max = 150, message = "no debe superar los 150 caracteres") String nombre,
        @NotBlank(message = "es obligatorio") String descripcion,
        @NotNull(message = "es obligatorio") @PastOrPresent(message = "no puede ser futura") LocalDate fechaObjeto,
        @NotNull(message = "es obligatorio") TipoObjeto tipo,
        @NotBlank(message = "es obligatorio")
        @Size(max = 255, message = "no debe superar los 255 caracteres") String ubicacion,
        @DecimalMin(value = "-90", message = "debe estar entre -90 y 90")
        @DecimalMax(value = "90", message = "debe estar entre -90 y 90") BigDecimal latitud,
        @DecimalMin(value = "-180", message = "debe estar entre -180 y 180")
        @DecimalMax(value = "180", message = "debe estar entre -180 y 180") BigDecimal longitud
) {
}
