package com.proyectointegrador.dto;
import jakarta.validation.constraints.NotNull;

/**
 * Preferencias de avisos de la cuenta propia.
 *
 * <p>H53 agrega {@code recordatorios}. Para no romper a los clientes de H24,
 * el cuarto campo es opcional: si no llega, el servicio conserva el valor
 * guardado (o {@code true} en una cuenta sin preferencias).</p>
 *
 * <p>H68 agrega los canales {@code push} y {@code correo}, también opcionales: si no llegan,
 * se conserva lo guardado (por defecto push sí y correo no).</p>
 */
public record PreferenciasRequest(@NotNull Boolean coincidencias, @NotNull Boolean contactos,
                                  @NotNull Boolean reclamos, Boolean recordatorios,
                                  Boolean push, Boolean correo) {

    /** Constructor compatible con el contrato de tres avisos de H24. */
    public PreferenciasRequest(Boolean coincidencias, Boolean contactos, Boolean reclamos) {
        this(coincidencias, contactos, reclamos, null, null, null);
    }

    /** Constructor compatible con H53, sin los canales de H68 (se conservan los guardados). */
    public PreferenciasRequest(Boolean coincidencias, Boolean contactos, Boolean reclamos, Boolean recordatorios) {
        this(coincidencias, contactos, reclamos, recordatorios, null, null);
    }
}
