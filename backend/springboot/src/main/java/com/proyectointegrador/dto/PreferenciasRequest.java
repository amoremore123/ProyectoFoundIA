package com.proyectointegrador.dto;
import jakarta.validation.constraints.NotNull;

/**
 * Preferencias de avisos de la cuenta propia.
 *
 * <p>H53 agrega {@code recordatorios}. Para no romper a los clientes de H24,
 * el cuarto campo es opcional: si no llega, el servicio conserva el valor
 * guardado (o {@code true} en una cuenta sin preferencias).</p>
 *
 * <p>H68 agrega {@code canales}: el canal de cada tipo de aviso. También es opcional,
 * y cada una de sus claves: lo que no llega conserva lo guardado (por defecto {@code PUSH}).</p>
 */
public record PreferenciasRequest(@NotNull Boolean coincidencias, @NotNull Boolean contactos,
                                  @NotNull Boolean reclamos, Boolean recordatorios,
                                  CanalesAvisos canales) {

    /** Constructor compatible con el contrato de tres avisos de H24. */
    public PreferenciasRequest(Boolean coincidencias, Boolean contactos, Boolean reclamos) {
        this(coincidencias, contactos, reclamos, null, null);
    }

    /** Constructor compatible con H53, sin los canales de H68 (se conservan los guardados). */
    public PreferenciasRequest(Boolean coincidencias, Boolean contactos, Boolean reclamos, Boolean recordatorios) {
        this(coincidencias, contactos, reclamos, recordatorios, null);
    }
}
