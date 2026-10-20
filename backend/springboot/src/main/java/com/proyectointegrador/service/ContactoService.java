package com.proyectointegrador.service;

import com.proyectointegrador.dto.ContactoRequest;
import com.proyectointegrador.dto.ContactoResponse;
import com.proyectointegrador.entity.Coincidencia;
import com.proyectointegrador.entity.Contacto;
import com.proyectointegrador.entity.EstadoCoincidencia;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.NivelCoincidencia;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.exception.SolicitudContactoInvalidaException;
import com.proyectointegrador.repository.CoincidenciaRepository;
import com.proyectointegrador.repository.ContactoRepository;
import com.proyectointegrador.repository.ObjetoRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class ContactoService {

    private final ObjetoRepository objetoRepository;
    private final CoincidenciaRepository coincidenciaRepository;
    private final ContactoRepository contactoRepository;
    private final NotificacionService notificacionService;

    public ContactoService(ObjetoRepository objetoRepository, CoincidenciaRepository coincidenciaRepository,
                           ContactoRepository contactoRepository, NotificacionService notificacionService) {
        this.objetoRepository = objetoRepository;
        this.coincidenciaRepository = coincidenciaRepository;
        this.contactoRepository = contactoRepository;
        this.notificacionService = notificacionService;
    }

    @Transactional
    public ContactoResponse iniciar(ContactoRequest request, Long usuarioId) {
        var perdido = objetoRepository.findById(request.objetoPerdidoId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Objeto perdido no encontrado con id " + request.objetoPerdidoId()));
        if (!perdido.getUsuario().getId().equals(usuarioId)) {
            throw new AccessDeniedException("Solo el autor del objeto perdido puede iniciar el contacto");
        }
        if (perdido.getTipo() != TipoObjeto.PERDIDO || perdido.getEstado() != EstadoObjeto.ACTIVO) {
            throw new SolicitudContactoInvalidaException("El objeto de origen debe ser una publicación perdida activa");
        }

        var encontrado = objetoRepository.findById(request.objetoEncontradoId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Objeto encontrado no encontrado con id " + request.objetoEncontradoId()));
        if (encontrado.getUsuario().getId().equals(usuarioId)) {
            throw new AccessDeniedException("No puedes iniciar contacto con tu propia publicación");
        }
        if (encontrado.getTipo() != TipoObjeto.ENCONTRADO || encontrado.getEstado() != EstadoObjeto.ACTIVO) {
            throw new SolicitudContactoInvalidaException("El objeto de destino debe ser un hallazgo activo");
        }
        if (!perdido.getCategoria().getId().equals(encontrado.getCategoria().getId())) {
            throw new SolicitudContactoInvalidaException("Los objetos deben pertenecer a la misma categoría");
        }

        Coincidencia coincidencia = coincidenciaRepository
                .findByObjetoPerdidoIdAndObjetoEncontradoId(perdido.getId(), encontrado.getId())
                .orElseGet(() -> crearCoincidencia(perdido, encontrado));
        var anterior = contactoRepository.findFirstByCoincidenciaIdAndUsuarioEmisorIdOrderByIdAsc(
                coincidencia.getId(), usuarioId);
        if (anterior.isPresent()) {
            return ContactoResponse.from(anterior.get(), true);
        }

        Contacto contacto = new Contacto();
        contacto.setCoincidencia(coincidencia);
        contacto.setUsuarioEmisor(perdido.getUsuario());
        contacto.setUsuarioReceptor(encontrado.getUsuario());
        // La tabla requiere mensaje; solo se guarda una referencia neutral, sin datos de contacto.
        contacto.setMensaje("Solicitud de contacto por coincidencia con publicación #" + encontrado.getId());
        Contacto guardado = contactoRepository.saveAndFlush(contacto);
        notificacionService.notificarSolicitudContacto(encontrado.getUsuario(), encontrado.getId());
        return ContactoResponse.from(guardado, false);
    }

    private Coincidencia crearCoincidencia(com.proyectointegrador.entity.Objeto perdido,
                                           com.proyectointegrador.entity.Objeto encontrado) {
        boolean ubicacionCoincide = perdido.getUbicacion() != null && encontrado.getUbicacion() != null
                && perdido.getUbicacion().trim().equalsIgnoreCase(encontrado.getUbicacion().trim());
        Coincidencia coincidencia = new Coincidencia();
        coincidencia.setObjetoPerdido(perdido);
        coincidencia.setObjetoEncontrado(encontrado);
        coincidencia.setPorcentaje(new BigDecimal(ubicacionCoincide ? "90.00" : "65.00"));
        coincidencia.setNivel(ubicacionCoincide ? NivelCoincidencia.ALTA : NivelCoincidencia.MEDIA);
        coincidencia.setEstado(EstadoCoincidencia.PENDIENTE);
        return coincidenciaRepository.saveAndFlush(coincidencia);
    }
}
