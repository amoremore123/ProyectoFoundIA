package com.proyectointegrador.service;

import java.util.List;

/**
 * H45: forma cruda que devuelve el proveedor de IA antes de validarla.
 * No se expone al cliente.
 */
public record IaInterpretacion(
        String intencion,
        String tipoBuscado,
        Long categoriaId,
        String ubicacion,
        List<String> palabras,
        List<String> colores
) {
}
