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
        return new ReclamoResponse(null, objetoId, "SIN_RECLAMO", null, false);
    }

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
