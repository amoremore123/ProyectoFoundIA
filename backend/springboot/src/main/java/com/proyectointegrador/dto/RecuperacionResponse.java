package com.proyectointegrador.dto;

import java.time.Instant;

public record RecuperacionResponse(String id, String estado, Long objetoPerdidoId,
                                   String estadoObjetoPerdido, Instant fechaRecuperacion, boolean repetido) {
}
