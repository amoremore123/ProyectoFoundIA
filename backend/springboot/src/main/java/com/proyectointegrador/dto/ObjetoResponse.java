package com.proyectointegrador.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record ObjetoResponse(
        Long id,
        String nombre,
        String descripcion,
        String ubicacion,
        BigDecimal latitud,
        BigDecimal longitud,
        LocalDate fechaObjeto,
        String tipo,
        String estado,
        LocalDateTime fechaPublicacion,
        CategoriaResumen categoria,
        UsuarioResumen publicadoPor,
        List<FotoResponse> fotos
) {
}
