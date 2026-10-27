package com.proyectointegrador.service;

import com.proyectointegrador.dto.CoincidenciaEstadoResponse;
import com.proyectointegrador.entity.Coincidencia;
import com.proyectointegrador.entity.EstadoCoincidencia;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.NivelCoincidencia;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.exception.EstadoCoincidenciaInvalidoException;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.repository.CoincidenciaRepository;
import com.proyectointegrador.repository.ObjetoRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class GestionCoincidenciaService {
    private final ObjetoRepository objetos;
    private final CoincidenciaRepository coincidencias;
    private final CoincidenciaService motor;

    public GestionCoincidenciaService(ObjetoRepository objetos, CoincidenciaRepository coincidencias,
                                     CoincidenciaService motor) {
        this.objetos = objetos;
        this.coincidencias = coincidencias;
        this.motor = motor;
    }

    @Transactional
    public CoincidenciaEstadoResponse descartar(String id, Long usuarioId) {
        BloqueoPareja.Objetos pareja = parejaPropia(id, usuarioId);
        Objeto perdido = pareja.perdido();
        Objeto encontrado = pareja.encontrado();
        validarActivos(perdido, encontrado);
        Coincidencia coincidencia = coincidencias
                .buscarParejaParaActualizar(perdido.getId(), encontrado.getId())
                .orElseGet(() -> nueva(perdido, encontrado));
        if (coincidencia.getEstado() == EstadoCoincidencia.ACEPTADA) {
            throw new EstadoCoincidenciaInvalidoException("Una coincidencia aceptada no se puede descartar");
        }
        boolean repetido = coincidencia.getEstado() == EstadoCoincidencia.DESCARTADA;
        if (!repetido) {
            coincidencia.setEstado(EstadoCoincidencia.DESCARTADA);
            coincidencias.saveAndFlush(coincidencia);
        }
        return new CoincidenciaEstadoResponse(clave(perdido, encontrado), coincidencia.getEstado().name(), repetido);
    }

    private BloqueoPareja.Objetos parejaPropia(String id, Long usuarioId) {
        long[] ids = motor.identificar(id);
        BloqueoPareja.Objetos pareja = BloqueoPareja.obtener(objetos, ids[0], ids[1]);
        Objeto perdido = pareja.perdido();
        Objeto encontrado = pareja.encontrado();
        if (!perdido.getUsuario().getId().equals(usuarioId)) {
            throw new AccessDeniedException("Solo el titular del objeto perdido puede gestionar la coincidencia");
        }
        if (perdido.getTipo() != TipoObjeto.PERDIDO || encontrado.getTipo() != TipoObjeto.ENCONTRADO
                || perdido.getUsuario().getId().equals(encontrado.getUsuario().getId())
                || !perdido.getCategoria().getId().equals(encontrado.getCategoria().getId())) {
            throw noDisponible();
        }
        if (perdido.getEstado() == EstadoObjeto.OCULTO || perdido.getEstado() == EstadoObjeto.ELIMINADO
                || encontrado.getEstado() == EstadoObjeto.OCULTO || encontrado.getEstado() == EstadoObjeto.ELIMINADO) {
            throw noDisponible();
        }
        if (motor.puntuar(perdido, encontrado) < CoincidenciaService.UMBRAL_SIMILITUD) {
            throw noDisponible();
        }
        return pareja;
    }

    private void validarActivos(Objeto perdido, Objeto encontrado) {
        if (perdido.getEstado() != EstadoObjeto.ACTIVO || encontrado.getEstado() != EstadoObjeto.ACTIVO) {
            throw new EstadoCoincidenciaInvalidoException("Los objetos deben seguir activos para gestionar la coincidencia");
        }
    }

    private Coincidencia nueva(Objeto perdido, Objeto encontrado) {
        int puntos = motor.puntuar(perdido, encontrado);
        Coincidencia coincidencia = new Coincidencia();
        coincidencia.setObjetoPerdido(perdido);
        coincidencia.setObjetoEncontrado(encontrado);
        coincidencia.setPorcentaje(BigDecimal.valueOf(puntos));
        coincidencia.setNivel(NivelCoincidencia.valueOf(motor.nivel(puntos)));
        return coincidencia;
    }

    private String clave(Objeto perdido, Objeto encontrado) {
        return perdido.getId() + "-" + encontrado.getId();
    }

    private ResourceNotFoundException noDisponible() {
        return new ResourceNotFoundException("Coincidencia no disponible");
    }
}
