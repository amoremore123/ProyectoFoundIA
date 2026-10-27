package com.proyectointegrador.service;

import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.repository.ObjetoRepository;

/** Todas las escrituras de una pareja toman los bloqueos en el mismo orden. */
final class BloqueoPareja {
    private BloqueoPareja() {
    }

    record Objetos(Objeto perdido, Objeto encontrado) {
    }

    static Objetos obtener(ObjetoRepository objetos, long perdidoId, long encontradoId) {
        long menor = Math.min(perdidoId, encontradoId);
        long mayor = Math.max(perdidoId, encontradoId);
        Objeto primero = bloquear(objetos, menor);
        Objeto segundo = menor == mayor ? primero : bloquear(objetos, mayor);
        return perdidoId == menor ? new Objetos(primero, segundo) : new Objetos(segundo, primero);
    }

    private static Objeto bloquear(ObjetoRepository objetos, long id) {
        return objetos.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Objeto no encontrado con id " + id));
    }
}
