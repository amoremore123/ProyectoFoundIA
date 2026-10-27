package com.proyectointegrador.service;

import com.proyectointegrador.entity.SolicitudEliminacion;
import com.proyectointegrador.repository.*;
import com.proyectointegrador.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class EliminacionCuentaService {
    public static final String INFORMACION = "Esta solicitud queda pendiente de revisión. No se han eliminado tu cuenta ni tus datos. Puedes consultar aquí su estado. No hay un plazo de borrado confirmado.";
    public record Estado(Long id, String estado, Instant fechaSolicitud, boolean repetido, String informacion) {}
    private final UsuarioRepository usuarios;
    private final SolicitudEliminacionRepository solicitudes;
    public EliminacionCuentaService(UsuarioRepository usuarios, SolicitudEliminacionRepository solicitudes) {
        this.usuarios = usuarios; this.solicitudes = solicitudes;
    }
    @Transactional(readOnly = true)
    public Estado consultar(Long usuarioId) {
        return solicitudes.findByUsuarioId(usuarioId).map(s -> respuesta(s, false))
            .orElseGet(() -> new Estado(null, "SIN_SOLICITUD", null, false, INFORMACION));
    }
    @Transactional
    public Estado solicitar(Long usuarioId) {
        // Serializa incluso dos primeras solicitudes simultáneas de la misma cuenta.
        var usuario = usuarios.findByIdForUpdate(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Cuenta no disponible"));
        var anterior = solicitudes.findByUsuarioId(usuarioId);
        if (anterior.isPresent()) return respuesta(anterior.get(), true);
        var solicitud = new SolicitudEliminacion(); solicitud.setUsuario(usuario);
        solicitud.setFechaSolicitud(Instant.now());
        return respuesta(solicitudes.saveAndFlush(solicitud), false);
    }
    private Estado respuesta(SolicitudEliminacion s, boolean repetido) {
        return new Estado(s.getId(), s.getEstado(), s.getFechaSolicitud(), repetido, INFORMACION);
    }
}
