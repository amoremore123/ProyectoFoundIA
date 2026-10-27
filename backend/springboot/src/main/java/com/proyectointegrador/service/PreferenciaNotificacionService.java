package com.proyectointegrador.service;

import com.proyectointegrador.dto.PreferenciasRequest;
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
            default -> throw new IllegalArgumentException("Tipo de aviso no admitido");
        };
    }

    private PreferenciasRequest respuesta(PreferenciaNotificacion p) {
        return new PreferenciasRequest(p.isCoincidencias(),p.isContactos(),p.isReclamos());
    }
}
