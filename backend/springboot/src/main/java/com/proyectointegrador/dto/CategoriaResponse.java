package com.proyectointegrador.dto;

import com.proyectointegrador.entity.Categoria;

public record CategoriaResponse(Long id, String nombre, String descripcion, Boolean estado) {

    public static CategoriaResponse from(Categoria categoria) {
        return new CategoriaResponse(
                categoria.getId(),
                categoria.getNombre(),
                categoria.getDescripcion(),
                categoria.getEstado()
        );
    }
}
