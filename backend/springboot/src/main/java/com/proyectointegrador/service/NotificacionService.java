package com.proyectointegrador.service;

import com.proyectointegrador.dto.HistorialNotificacionesResponse;
import com.proyectointegrador.dto.MarcarTodasResponse;
import com.proyectointegrador.dto.NotificacionResponse;
import com.proyectointegrador.entity.Notificacion;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.exception.SolicitudInvalidaException;
import com.proyectointegrador.repository.NotificacionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final PreferenciaNotificacionService preferencias;

    public NotificacionService(NotificacionRepository notificacionRepository, PreferenciaNotificacionService preferencias) {
        this.notificacionRepository = notificacionRepository;
        this.preferencias = preferencias;
    }

    @Transactional(readOnly = true)
    public List<NotificacionResponse> listar(Long usuarioId) {
        return notificacionRepository.findByUsuarioIdOrderByFechaCreacionDesc(usuarioId).stream()
                .map(NotificacionResponse::from)
                .toList();
    }

    public static final int TAMANO_MAXIMO = 50;

    /**
     * H25: historial propio con filtro TODAS, LEIDAS o NO_LEIDAS. Consultar no cambia ningún aviso.
     */
    @Transactional(readOnly = true)
    public HistorialNotificacionesResponse historial(Long usuarioId, String estado, int pagina, int tamano) {
        String filtro = estado == null || estado.isBlank() ? "TODAS" : estado.trim().toUpperCase();
        if (!filtro.equals("TODAS") && !filtro.equals("LEIDAS") && !filtro.equals("NO_LEIDAS")) {
            throw new SolicitudInvalidaException("Filtro inválido; usa TODAS, LEIDAS o NO_LEIDAS.");
        }
        if (pagina < 0) {
            throw new SolicitudInvalidaException("La página empieza en 0.");
        }
        if (tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new SolicitudInvalidaException("El tamaño de página va de 1 a " + TAMANO_MAXIMO + ".");
        }
        var orden = PageRequest.of(pagina, tamano, Sort.by(Sort.Order.desc("fechaCreacion"), Sort.Order.desc("id")));
        Page<Notificacion> resultado = switch (filtro) {
            case "LEIDAS" -> notificacionRepository.findByUsuarioIdAndLeida(usuarioId, Boolean.TRUE, orden);
            case "NO_LEIDAS" -> notificacionRepository.findByUsuarioIdAndLeida(usuarioId, Boolean.FALSE, orden);
            default -> notificacionRepository.findByUsuarioId(usuarioId, orden);
        };
        return new HistorialNotificacionesResponse(
                resultado.getContent().stream().map(NotificacionResponse::from).toList(),
                resultado.getNumber(),
                resultado.getSize(),
                resultado.getTotalElements(),
                resultado.getTotalPages(),
                filtro,
                notificacionRepository.countByUsuarioIdAndLeidaFalse(usuarioId)
        );
    }

    /** H25: marca como leídos solo los avisos de la cuenta autenticada. Repetirlo no cambia nada. */
    @Transactional
    public MarcarTodasResponse marcarTodasLeidas(Long usuarioId) {
        int actualizadas = notificacionRepository.marcarTodasLeidas(usuarioId);
        return new MarcarTodasResponse(actualizadas, notificacionRepository.countByUsuarioIdAndLeidaFalse(usuarioId));
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
        if (!preferencias.permite(destinatario.getId(), "CONTACTO_RECIBIDO")) return;
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
        if (!preferencias.permite(destinatario.getId(), "RECLAMO_RECIBIDO")) return;
        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(destinatario);
        notificacion.setTitulo("Nuevo reclamo de objeto");
        notificacion.setMensaje("Un usuario ha enviado un reclamo sobre tu publicación #" + objetoId
                + ". Revisa la solicitud para atenderla.");
        notificacion.setTipo("RECLAMO_RECIBIDO");
        notificacion.setLeida(Boolean.FALSE);
        notificacionRepository.save(notificacion);
    }

    @Transactional
    public Notificacion notificarCoincidencia(Usuario destinatario, Long perdidoId, Long encontradoId) {
        if (!preferencias.permite(destinatario.getId(), "COINCIDENCIA_NUEVA")) return null;
        Notificacion aviso = new Notificacion();
        aviso.setUsuario(destinatario);
        aviso.setTitulo("Nueva coincidencia");
        aviso.setMensaje("Hay una nueva coincidencia para tu publicación perdida #" + perdidoId + ".");
        aviso.setTipo("COINCIDENCIA_NUEVA");
        aviso.setEnlace("/coincidencias/" + perdidoId + "-" + encontradoId);
        return notificacionRepository.save(aviso);
    }
}
