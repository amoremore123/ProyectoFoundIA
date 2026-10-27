package com.proyectointegrador.service;

import com.proyectointegrador.entity.*;
import com.proyectointegrador.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistroAvisoCoincidenciaService {
    private final ObjetoRepository objetos;
    private final UsuarioRepository usuarios;
    private final CoincidenciaRepository coincidencias;
    private final AvisoCoincidenciaRepository avisos;
    private final CoincidenciaService motor;
    private final NotificacionService notificaciones;
    private final EntityManager entidades;

    public RegistroAvisoCoincidenciaService(ObjetoRepository objetos,UsuarioRepository usuarios,CoincidenciaRepository coincidencias,
            AvisoCoincidenciaRepository avisos,CoincidenciaService motor,NotificacionService notificaciones,EntityManager entidades) {
        this.objetos=objetos;this.usuarios=usuarios;this.coincidencias=coincidencias;this.avisos=avisos;
        this.motor=motor;this.notificaciones=notificaciones;this.entidades=entidades;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void registrar(long perdidoId,long encontradoId) {
        // Una sola pareja por transacción. Incluye los padres de las FK del aviso:
        // una FK también toma bloqueos y no debe invertir el orden de H21/H22/H18.
        var pareja=BloqueoPareja.obtener(objetos,perdidoId,encontradoId);
        var perdido=pareja.perdido();var encontrado=pareja.encontrado();
        if(perdido.getTipo()!=TipoObjeto.PERDIDO || encontrado.getTipo()!=TipoObjeto.ENCONTRADO
                || perdido.getEstado()!=EstadoObjeto.ACTIVO || encontrado.getEstado()!=EstadoObjeto.ACTIVO
                || perdido.getUsuario().getId().equals(encontrado.getUsuario().getId())
                || !perdido.getCategoria().getId().equals(encontrado.getCategoria().getId())
                || motor.puntuar(perdido,encontrado)<CoincidenciaService.UMBRAL_SIMILITUD)return;
        var existente=coincidencias.buscarParejaParaActualizar(perdidoId,encontradoId);
        if(existente.isPresent() && (existente.get().getEstado()==EstadoCoincidencia.DESCARTADA
                || existente.get().getEstado()==EstadoCoincidencia.ACEPTADA))return;
        var titular=usuarios.findByIdForUpdate(perdido.getUsuario().getId()).orElseThrow();
        entidades.refresh(titular,LockModeType.PESSIMISTIC_WRITE);
        if(titular.getEstado()!=EstadoUsuario.ACTIVO || !titular.isVerificado()
                || avisos.buscarParaActualizar(perdidoId,encontradoId).isPresent())return;
        var aviso=new AvisoCoincidencia();aviso.setObjetoPerdido(perdido);aviso.setObjetoEncontrado(encontrado);
        aviso.setNotificacion(notificaciones.notificarCoincidencia(titular,perdidoId,encontradoId));
        avisos.saveAndFlush(aviso);
    }
}
