package com.proyectointegrador.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** H67: visita que llegó por el enlace de una campaña. {@code visitaId} lo genera la web, al azar. */
public record VisitaCampanaRequest(
        @NotNull(message = "es obligatorio")
        @Pattern(regexp = AlcanceFormato.ORIGEN, message = "debe tener de 3 a 40 minúsculas, números o guiones")
        String origen,

        @NotNull(message = "es obligatorio")
        @Pattern(regexp = "[A-Za-z0-9_-]{8,64}", message = "debe tener de 8 a 64 letras, números, guiones o guiones bajos")
        String visitaId
) {
    /** Formato del origen de H60. */
    public static final class AlcanceFormato {
        public static final String ORIGEN = "(?=.{3,40}$)[a-z0-9]+(?:-[a-z0-9]+)*";

        private AlcanceFormato() {
        }
    }
}
