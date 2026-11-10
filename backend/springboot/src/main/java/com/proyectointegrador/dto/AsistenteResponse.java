package com.proyectointegrador.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * H45: interpretación que consume el asistente web/Android.
 * Los campos extra (fuente, resumenPropio, datosPropios) no cambian el contrato
 * que ya usa el frontend: intencion, tipoBuscado, categoria.id, ubicacion,
 * palabras y colores.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AsistenteResponse(
        String intencion,
        String tipoBuscado,
        CategoriaAsistente categoria,
        String ubicacion,
        List<String> palabras,
        List<String> colores,
        String fuente,
        String resumenPropio,
        DatosPropios datosPropios
) {

    public record CategoriaAsistente(Long id, String nombre) {
    }

    /** Solo cifras: nunca nombres, correos, teléfonos ni tokens de QR. */
    public record DatosPropios(
            Publicaciones publicaciones,
            Reclamos reclamos,
            long qrRetiroVigentes
    ) {
    }

    public record Publicaciones(long total, long activas, long enCustodia, long recuperadas) {
    }

    public record Reclamos(long total, long pendientes, long aceptados, long retirados, long rechazados) {
    }
}
