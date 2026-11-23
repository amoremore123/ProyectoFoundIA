package com.proyectointegrador.service;

import com.proyectointegrador.dto.CanalesAvisos;
import com.proyectointegrador.dto.PreferenciasRequest;
import com.proyectointegrador.entity.CanalAviso;
import com.proyectointegrador.entity.PreferenciaNotificacion;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
public class PreferenciaNotificacionService {
    private final PreferenciaNotificacionRepository preferencias;
    private final UsuarioRepository usuarios;
    public PreferenciaNotificacionService(PreferenciaNotificacionRepository preferencias, UsuarioRepository usuarios) {
        this.preferencias=preferencias; this.usuarios=usuarios;
    }

    @Transactional(readOnly=true)
    public PreferenciasRequest obtener(Long usuarioId) {
        return respuesta(preferencias.findById(usuarioId).orElseGet(PreferenciaNotificacion::new));
    }

    @Transactional
    public PreferenciasRequest guardar(Long usuarioId, PreferenciasRequest datos) {
        usuarios.findByIdForUpdate(usuarioId).orElseThrow(() -> new InvalidCredentialsException("No autenticado"));
        var p=preferencias.buscarParaActualizar(usuarioId).orElseGet(PreferenciaNotificacion::new);
        p.setUsuarioId(usuarioId); p.setCoincidencias(datos.coincidencias()); p.setContactos(datos.contactos());
        p.setReclamos(datos.reclamos());
        // H53: un cliente de H24 que no envía `recordatorios` conserva el valor guardado.
        if (datos.recordatorios()!=null) p.setRecordatorios(datos.recordatorios());
        // H68: los clientes anteriores no envían los canales; cada canal que no llega se conserva.
        CanalesAvisos c=datos.canales();
        if (c!=null) {
            if (c.coincidencias()!=null) p.setCanalCoincidencias(c.coincidencias());
            if (c.contactos()!=null) p.setCanalContactos(c.contactos());
            if (c.reclamos()!=null) p.setCanalReclamos(c.reclamos());
            if (c.recordatorios()!=null) p.setCanalRecordatorios(c.recordatorios());
            if (c.mensajes()!=null) p.setCanalMensajes(c.mensajes());
            if (c.sistema()!=null) p.setCanalSistema(c.sistema());
        }
        p.setFechaActualizacion(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
        return respuesta(preferencias.saveAndFlush(p));
    }

    @Transactional
    public boolean permite(Long usuarioId, String tipo) {
        // La inserción posterior de notificaciones también bloquea esta FK padre.
        // Mantener usuario -> preferencia evita invertir el orden del primer PUT.
        usuarios.findByIdForUpdate(usuarioId).orElseThrow(() -> new InvalidCredentialsException("Destinatario no disponible"));
        var p=preferencias.buscarParaActualizar(usuarioId).orElseGet(PreferenciaNotificacion::new);
        return switch(tipo) {
            case "COINCIDENCIA_NUEVA" -> p.isCoincidencias();
            case "CONTACTO_RECIBIDO" -> p.isContactos();
            case "RECLAMO_RECIBIDO" -> p.isReclamos();
            case "RECORDATORIO_OBJETO" -> p.isRecordatorios();
            case "MENSAJE_CHAT" -> true;
            default -> throw new IllegalArgumentException("Tipo de aviso no admitido");
        };
    }

    private PreferenciasRequest respuesta(PreferenciaNotificacion p) {
        return new PreferenciasRequest(p.isCoincidencias(),p.isContactos(),p.isReclamos(),p.isRecordatorios(),
                new CanalesAvisos(p.getCanalCoincidencias(),p.getCanalContactos(),p.getCanalReclamos(),
                        p.getCanalRecordatorios(),p.getCanalMensajes(),p.getCanalSistema()));
    }

    /** H68: canal elegido para un tipo de aviso; {@code NINGUNO} si el tipo es desconocido. */
    public static CanalAviso canalDe(PreferenciasRequest p, String tipo) {
        CanalesAvisos c=p.canales();
        if (c==null || tipo==null) return CanalAviso.NINGUNO;
        CanalAviso canal=switch (tipo) {
            case "COINCIDENCIA_NUEVA" -> c.coincidencias();
            case "CONTACTO_RECIBIDO" -> c.contactos();
            case "RECLAMO_RECIBIDO", "RECLAMO" -> c.reclamos();
            case "RECORDATORIO_OBJETO" -> c.recordatorios();
            case "MENSAJE_CHAT" -> c.mensajes();
            case "SISTEMA" -> c.sistema();
            default -> CanalAviso.NINGUNO;
        };
        return canal==null ? CanalAviso.PUSH : canal;
    }

    /** H68: el aviso sale por push si su tipo está activo y su canal incluye push. */
    public static boolean permitePush(PreferenciasRequest p, String tipo) {
        return tipoActivo(p, tipo) && canalDe(p, tipo).incluyePush();
    }

    /** H68: el aviso sale por correo si su tipo está activo y su canal incluye correo. */
    public static boolean permiteCorreo(PreferenciasRequest p, String tipo) {
        return tipoActivo(p, tipo) && canalDe(p, tipo).incluyeCorreo();
    }

    /**
     * H57 y H68: si el tipo de aviso (de Spring o de Django) está activo en las preferencias, para
     * decidir si sale por push o por correo. Los tipos desconocidos no salen de la bandeja.
     */
    public static boolean tipoActivo(PreferenciasRequest p, String tipo) {
        if (tipo == null) return false;
        return switch (tipo) {
            case "COINCIDENCIA_NUEVA" -> Boolean.TRUE.equals(p.coincidencias());
            case "CONTACTO_RECIBIDO" -> Boolean.TRUE.equals(p.contactos());
            case "RECLAMO_RECIBIDO", "RECLAMO" -> Boolean.TRUE.equals(p.reclamos());
            case "RECORDATORIO_OBJETO" -> !Boolean.FALSE.equals(p.recordatorios());
            case "MENSAJE_CHAT", "SISTEMA" -> true;
            default -> false;
        };
    }
}
