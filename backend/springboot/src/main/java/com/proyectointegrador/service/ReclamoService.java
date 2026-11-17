package com.proyectointegrador.service;

import com.proyectointegrador.dto.ReclamoResponse;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Reclamo;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.exception.ReclamoInvalidoException;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.repository.ObjetoRepository;
import com.proyectointegrador.repository.ReclamoRepository;
import com.proyectointegrador.repository.UsuarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReclamoService {

    /** Estados de un reclamo que ya entrega el objeto a alguien (no se aceptan reclamos nuevos). */
    static final java.util.List<String> ESTADOS_ASIGNADO = java.util.List.of("ACEPTADO", "RETIRADO");
    public static final String ASIGNADO_A_OTRO = "ASIGNADO_A_OTRO";

    private final ObjetoRepository objetoRepository;
    private final ReclamoRepository reclamoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

    public ReclamoService(ObjetoRepository objetoRepository, ReclamoRepository reclamoRepository,
                          UsuarioRepository usuarioRepository, NotificacionService notificacionService) {
        this.objetoRepository = objetoRepository;
        this.reclamoRepository = reclamoRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionService = notificacionService;
    }

    @Transactional(readOnly = true)
    public ReclamoResponse miReclamoCustodia(Long objetoId, Long usuarioId) {
        var objeto = objetoRepository.findById(objetoId)
                .orElseThrow(() -> new ResourceNotFoundException("Objeto no encontrado"));
        var existente = reclamoRepository.findByObjetoIdAndUsuarioId(objetoId, usuarioId);
        if (existente.isPresent()) return ReclamoResponse.from(existente.get(), false);
        validarCustodia(objeto, usuarioId);
        // Otra persona ya fue aceptada: no se ofrece el formulario para reclamarlo
        if (reclamoRepository.existsByObjetoIdAndEstadoIn(objetoId, ESTADOS_ASIGNADO)) {
            return new ReclamoResponse(null, objetoId, ASIGNADO_A_OTRO, null, false);
        }
        return new ReclamoResponse(null, objetoId, "SIN_RECLAMO", null, false);
    }

    @Transactional(readOnly = true)
    public QrPropio qrPropio(Long objetoId, Long usuarioId) {
        Reclamo reclamo = reclamoRepository.findByObjetoIdAndUsuarioId(objetoId, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Reclamo no encontrado"));
        if (!"ACEPTADO".equals(reclamo.getEstado()) || reclamo.getQrRetiroToken() == null
                || reclamo.getQrRetiroUsadoEn() != null || reclamo.getQrRetiroExpira() == null
                || !reclamo.getQrRetiroExpira().isAfter(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))
                || reclamo.getObjeto().getEstado() != EstadoObjeto.EN_CUSTODIA) {
            throw new ReclamoInvalidoException("El QR de retiro ya no está disponible");
        }
        return new QrPropio(reclamo.getQrRetiroToken(), reclamo.getQrRetiroExpira());
    }

    public record QrPropio(String token, java.time.LocalDateTime expira) {}

    @Transactional
    public ReclamoResponse reclamarCustodia(Long objetoId, Long usuarioId, String respuesta) {
        return registrar(objetoId, usuarioId, respuesta);
    }

    @Transactional
    public ReclamoResponse reclamar(Long objetoId, Long usuarioId) {
        return registrar(objetoId, usuarioId, null);
    }

    private ReclamoResponse registrar(Long objetoId, Long usuarioId, String respuesta) {
        var objeto = objetoRepository.findByIdForUpdate(objetoId)
                .orElseThrow(() -> new ResourceNotFoundException("Objeto no encontrado con id " + objetoId));
        if (respuesta != null) {
            validarCustodia(objeto, usuarioId);
            respuesta = respuesta.strip();
            if (respuesta.length() < 3 || respuesta.length() > 500) {
                throw new ReclamoInvalidoException("Ingresa entre 3 y 500 caracteres de verificación");
            }
        } else if (objeto.getEstado() != EstadoObjeto.ACTIVO || objeto.getTipo() != TipoObjeto.ENCONTRADO) {
            throw new ReclamoInvalidoException("Para reclamar un objeto en custodia se requiere una respuesta de verificación");
        }
        if (objeto.getUsuario().getId().equals(usuarioId)) {
            throw new AccessDeniedException("No puedes reclamar tu propia publicación");
        }

        var existente = reclamoRepository.findByObjetoIdAndUsuarioId(objetoId, usuarioId);
        if (existente.isPresent()) {
            return ReclamoResponse.from(existente.get(), true);
        }
        if (respuesta != null && reclamoRepository.existsByObjetoIdAndEstadoIn(objetoId, ESTADOS_ASIGNADO)) {
            throw new ReclamoInvalidoException(
                    "Este objeto ya fue asignado a otra persona. Si crees que es tuyo, acércate a la biblioteca");
        }

        var usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id " + usuarioId));
        Reclamo reclamo = new Reclamo();
        reclamo.setObjeto(objeto);
        reclamo.setUsuario(usuario);
        reclamo.setRespuestaVerificacion(respuesta);
        Reclamo guardado = reclamoRepository.saveAndFlush(reclamo);
        if (respuesta == null) notificacionService.notificarReclamo(objeto.getUsuario(), objetoId);
        return ReclamoResponse.from(guardado, false);
    }

    private void validarCustodia(com.proyectointegrador.entity.Objeto objeto, Long usuarioId) {
        if (objeto.getEstado() != EstadoObjeto.EN_CUSTODIA || objeto.getTipo() != TipoObjeto.ENCONTRADO) {
            throw new ReclamoInvalidoException("El objeto encontrado no está en custodia");
        }
        if (objeto.getUsuario().getId().equals(usuarioId)) {
            throw new AccessDeniedException("No puedes reclamar tu propia publicación");
        }
    }
}
