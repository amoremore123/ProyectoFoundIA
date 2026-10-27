package com.proyectointegrador.service;

import com.proyectointegrador.dto.ReporteRequest;
import com.proyectointegrador.dto.ReporteResponse;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.EstadoUsuario;
import com.proyectointegrador.entity.Reporte;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.exception.SolicitudInvalidaException;
import com.proyectointegrador.repository.ObjetoRepository;
import com.proyectointegrador.repository.ReporteRepository;
import com.proyectointegrador.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Service
public class ReporteService {
    private final ObjetoRepository objetos;
    private final UsuarioRepository usuarios;
    private final ReporteRepository reportes;

    public ReporteService(ObjetoRepository objetos, UsuarioRepository usuarios, ReporteRepository reportes) {
        this.objetos = objetos;
        this.usuarios = usuarios;
        this.reportes = reportes;
    }

    @Transactional
    public ReporteResponse reportar(Long objetoId, Long usuarioId, ReporteRequest datos) {
        // El mismo bloqueo se usa para editar/retirar; dos envíos del reporte se serializan.
        var objeto = objetos.findByIdForUpdate(objetoId)
                .orElseThrow(() -> new ResourceNotFoundException("Publicación no disponible"));
        if (objeto.getEstado() != EstadoObjeto.ACTIVO && objeto.getEstado() != EstadoObjeto.RECUPERADO) {
            throw new ResourceNotFoundException("Publicación no disponible");
        }
        var usuario = usuarios.findById(usuarioId)
                .orElseThrow(() -> new InvalidCredentialsException("No autenticado"));
        if (usuario.getEstado() != EstadoUsuario.ACTIVO || !usuario.isVerificado()) {
            throw new InvalidCredentialsException("Cuenta no habilitada");
        }
        String descripcion = datos.descripcion() == null ? null : datos.descripcion().trim();
        if ("OTRO".equals(datos.motivo()) && (descripcion == null || descripcion.isBlank())) {
            throw new SolicitudInvalidaException("Explica el motivo cuando seleccionas OTRO");
        }
        var anterior = reportes.findFirstByObjetoIdAndUsuarioIdOrderByIdAsc(objetoId, usuarioId);
        if (anterior.isPresent()) {
            return respuesta(anterior.get(), true);
        }
        Reporte reporte = new Reporte();
        reporte.setObjeto(objeto);
        reporte.setUsuario(usuario);
        reporte.setMotivo(datos.motivo());
        reporte.setDescripcion(descripcion);
        reporte.setFechaReporte(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
        return respuesta(reportes.saveAndFlush(reporte), false);
    }

    private ReporteResponse respuesta(Reporte reporte, boolean repetido) {
        return new ReporteResponse(reporte.getId(), reporte.getObjeto().getId(), reporte.getMotivo(),
                reporte.getEstado().name(), reporte.getFechaReporte().toInstant(ZoneOffset.UTC), repetido);
    }
}
