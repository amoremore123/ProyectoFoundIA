package com.proyectointegrador.dto;

import com.proyectointegrador.entity.TipoObjeto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ObjetoRequest(
        @NotNull(message = "es obligatorio") Long categoriaId,
        @NotBlank(message = "es obligatorio") String nombre,
        @NotBlank(message = "es obligatorio") String descripcion,
        @NotNull(message = "es obligatorio") @PastOrPresent(message = "no puede ser futura") LocalDate fechaObjeto,
        @NotNull(message = "es obligatorio") TipoObjeto tipo,
        String ubicacion,
        BigDecimal latitud,
        BigDecimal longitud
) {
}
