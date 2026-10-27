package com.proyectointegrador.dto;
import jakarta.validation.constraints.NotNull;

public record PreferenciasRequest(@NotNull Boolean coincidencias, @NotNull Boolean contactos,
                                  @NotNull Boolean reclamos) {
}
