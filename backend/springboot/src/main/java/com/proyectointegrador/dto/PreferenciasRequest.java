package com.proyectointegrador.dto;
import jakarta.validation.constraints.NotNull;

/**
 * Preferencias de avisos de la cuenta propia.
 *
 * <p>H53 agrega {@code recordatorios}. Para no romper a los clientes de H24,
 * el cuarto campo es opcional: si no llega, el servicio conserva el valor
 * guardado (o {@code true} en una cuenta sin preferencias).</p>
 */
public record PreferenciasRequest(@NotNull Boolean coincidencias, @NotNull Boolean contactos,
                                  @NotNull Boolean reclamos, Boolean recordatorios) {

    /** Constructor compatible con el contrato de tres avisos de H24. */
    public PreferenciasRequest(Boolean coincidencias, Boolean contactos, Boolean reclamos) {
        this(coincidencias, contactos, reclamos, null);
    }
}
