package com.proyectointegrador.dto;

import com.proyectointegrador.entity.TipoObjeto;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

public record FiltroBusqueda(String q, TipoObjeto tipo, Long categoriaId, String ubicacion,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
        Double latitud, Double longitud, Double radioKm) {
}
