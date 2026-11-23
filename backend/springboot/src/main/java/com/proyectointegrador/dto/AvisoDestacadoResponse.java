package com.proyectointegrador.dto;

import java.time.LocalDate;

/** H71: aviso destacado vigente, sin datos de quién lo creó. */
public record AvisoDestacadoResponse(Long id, String titulo, String texto, String enlace, LocalDate fechaFin) {
}
