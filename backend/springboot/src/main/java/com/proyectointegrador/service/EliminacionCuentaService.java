package com.proyectointegrador.service;

import com.proyectointegrador.entity.SolicitudEliminacion;
import com.proyectointegrador.repository.*;
import com.proyectointegrador.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class EliminacionCuentaService {
    /** H34: ahora la solicitud sí se atiende (Ley 29733, derecho de cancelación). */
    public static final String INFORMACION = "Un administrador revisará tu solicitud en un plazo de hasta 10 días hábiles. "
            + "Al atenderla se eliminarán o anonimizarán tu cuenta y tus datos personales, y te avisaremos por correo. "
            + "No se puede deshacer. Se conservan, sin tus datos, los registros que la biblioteca debe guardar.";
    public record Estado(Long id, String estado, Instant fechaSolicitud, boolean repetido, String informacion,
                         String motivoRechazo) {}
    private final UsuarioRepository usuarios;
    private final SolicitudEliminacionRepository solicitudes;
    public EliminacionCuentaService(UsuarioRepository usuarios, SolicitudEliminacionRepository solicitudes) {
        this.usuarios = usuarios; this.solicitudes = solicitudes;
    }
    @Transactional(readOnly = true)
    public Estado consultar(Long usuarioId) {
        return solicitudes.findByUsuarioId(usuarioId).map(s -> respuesta(s, false))
            .orElseGet(() -> new Estado(null, "SIN_SOLICITUD", null, false, INFORMACION, null));
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
        return new Estado(s.getId(), s.getEstado(), s.getFechaSolicitud(), repetido, INFORMACION, s.getMotivoRechazo());
    }
}
