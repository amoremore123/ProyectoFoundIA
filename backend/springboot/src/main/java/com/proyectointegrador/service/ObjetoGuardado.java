package com.proyectointegrador.service;

/** Señal posterior al guardado; el evento durable se procesa después del commit. Los GET no lo emiten. */
public record ObjetoGuardado(Long id) {
}
