package com.proyectointegrador.service;

import com.proyectointegrador.dto.NotificacionResponse;
import com.proyectointegrador.entity.Notificacion;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.repository.NotificacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;

    public NotificacionService(NotificacionRepository notificacionRepository) {
        this.notificacionRepository = notificacionRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificacionResponse> listar(Long usuarioId) {
        return notificacionRepository.findByUsuarioIdOrderByFechaCreacionDesc(usuarioId).stream()
                .map(NotificacionResponse::from)
                .toList();
    }

    @Transactional
    public NotificacionResponse marcarLeida(Long id, Long usuarioId) {
        var notificacion = notificacionRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificación no encontrada con id " + id));
        notificacion.setLeida(Boolean.TRUE);
        notificacionRepository.save(notificacion);
        return NotificacionResponse.from(notificacion);
    }

    @Transactional
    public void notificarSolicitudContacto(Usuario destinatario, Long objetoEncontradoId) {
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(destinatario);
        notificacion.setTitulo("Nueva solicitud de contacto");
        notificacion.setMensaje("Alguien desea contactarte por tu publicación de objeto encontrado #"
                + objetoEncontradoId + ".");
        notificacion.setTipo("CONTACTO_RECIBIDO");
        notificacion.setLeida(Boolean.FALSE);
        notificacionRepository.save(notificacion);
    }

    @Transactional
    public void notificarReclamo(Usuario destinatario, Long objetoId) {
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(destinatario);
        notificacion.setTitulo("Nuevo reclamo de objeto");
        notificacion.setMensaje("Un usuario ha enviado un reclamo sobre tu publicación #" + objetoId
                + ". Revisa la solicitud para atenderla.");
        notificacion.setTipo("RECLAMO_RECIBIDO");
        notificacion.setLeida(Boolean.FALSE);
        notificacionRepository.save(notificacion);
    }
}
