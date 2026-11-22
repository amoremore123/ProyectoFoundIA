package com.proyectointegrador.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Contratos del chat (H55 a H63). Ver docs/api/sprint4-h55.md. */
public final class ChatDtos {

    private ChatDtos() {
    }

    /** Se indica exactamente uno: contactoId (entre personas) o reclamoId (con la biblioteca). */
    public record IniciarRequest(Long contactoId, Long reclamoId) {
    }

    public record EnviarRequest(@NotBlank @Size(max = 1000) String texto) {
    }

    public record ReportarRequest(@NotBlank @Size(min = 3, max = 255) String motivo) {
    }

    public record Participante(Long id, String nombre) {
    }

    public record MensajeResponse(Long id, Long autorId, String autorNombre, String texto, String imagenUrl,
                                  LocalDateTime enviadoEn) {
    }

    public record ConversacionResponse(Long id, String tipo, Long objetoId, String objetoNombre, String estado,
                                       boolean soloLectura, boolean bloqueada, Participante otro,
                                       MensajeResponse ultimoMensaje, long sinLeer) {
    }

    public record MensajesResponse(List<MensajeResponse> mensajes, long ultimoId, boolean soloLectura,
                                   boolean bloqueada) {
    }
}
