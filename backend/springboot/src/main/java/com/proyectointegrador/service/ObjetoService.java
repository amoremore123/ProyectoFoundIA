package com.proyectointegrador.service;

import com.proyectointegrador.dto.FotoResponse;
import com.proyectointegrador.dto.ObjetoRequest;
import com.proyectointegrador.dto.ObjetoResponse;
import com.proyectointegrador.dto.CategoriaResumen;
import com.proyectointegrador.dto.UsuarioResumen;
import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.entity.EventoCoincidencia;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.repository.CategoriaRepository;
import com.proyectointegrador.repository.ObjetoRepository;
import com.proyectointegrador.repository.UsuarioRepository;
import com.proyectointegrador.repository.EventoCoincidenciaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.time.ZoneOffset;

@Service
public class ObjetoService {

    private static final List<EstadoObjeto> ESTADOS_PUBLICOS = List.of(EstadoObjeto.ACTIVO, EstadoObjeto.RECUPERADO);
    private static final Sort ORDEN_PUBLICACION = Sort.by(Sort.Direction.DESC, "fechaPublicacion", "id");

    private final ObjetoRepository objetoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;
    private final ApplicationEventPublisher eventos;
    private final EventoCoincidenciaRepository eventosPendientes;

    public ObjetoService(ObjetoRepository objetoRepository,
                         CategoriaRepository categoriaRepository,
                         UsuarioRepository usuarioRepository, ApplicationEventPublisher eventos, EventoCoincidenciaRepository eventosPendientes) {
        this.objetoRepository = objetoRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
        this.eventos = eventos;
        this.eventosPendientes = eventosPendientes;
    }

    @Transactional(readOnly = true)
    public List<ObjetoResponse> listar(TipoObjeto tipo, EstadoObjeto estado, Long categoriaId) {
        validarCategoria(categoriaId);
        Specification<Objeto> specification =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(root.get("estado").in(ESTADOS_PUBLICOS));
                    if (tipo != null) {
                        predicates.add(cb.equal(root.get("tipo"), tipo));
                    }
                    if (estado != null) {
                        predicates.add(cb.equal(root.get("estado"), estado));
                    }
                    if (categoriaId != null) {
                        predicates.add(cb.equal(root.get("categoria").get("id"), categoriaId));
                    }
                    return cb.and(predicates.toArray(new Predicate[0]));
                };

        return objetoRepository.findAll(specification, ORDEN_PUBLICACION)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ObjetoResponse obtener(Long id) {
        return toResponse(buscarPublico(id));
    }

    @Transactional
    public ObjetoResponse crear(ObjetoRequest request, Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id " + usuarioId));
        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con id " + request.categoriaId()));

        exigirCategoriaActiva(categoria);

        Objeto objeto = new Objeto();
        objeto.setUsuario(usuario);
        objeto.setCategoria(categoria);
        objeto.setNombre(request.nombre());
        objeto.setDescripcion(request.descripcion());
        objeto.setFechaObjeto(request.fechaObjeto());
        objeto.setTipo(request.tipo());
        objeto.setUbicacion(request.ubicacion());
        objeto.setLatitud(request.latitud());
        objeto.setLongitud(request.longitud());
        objeto.setEstado(EstadoObjeto.ACTIVO);

        objetoRepository.save(objeto);
        registrarEvento(objeto);
        return toResponse(objeto);
    }

    @Transactional
    public ObjetoResponse actualizar(Long id, ObjetoRequest request, Long usuarioId) {
        Objeto objeto = buscarParaActualizar(id);
        verificarAutor(objeto, usuarioId);
        if (objeto.getEstado() == EstadoObjeto.ELIMINADO) {
            throw new ResourceNotFoundException("Objeto no encontrado con id " + id);
        }

        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con id " + request.categoriaId()));

        if (!categoria.getId().equals(objeto.getCategoria().getId())) {
            exigirCategoriaActiva(categoria);
        }
        objeto.setCategoria(categoria);
        objeto.setNombre(request.nombre());
        objeto.setDescripcion(request.descripcion());
        objeto.setFechaObjeto(request.fechaObjeto());
        objeto.setTipo(request.tipo());
        objeto.setUbicacion(request.ubicacion());
        objeto.setLatitud(request.latitud());
        objeto.setLongitud(request.longitud());

        objetoRepository.save(objeto);
        registrarEvento(objeto);
        return toResponse(objeto);
    }

    @Transactional
    public void eliminar(Long id, Long usuarioId) {
        Objeto objeto = buscarParaActualizar(id);
        verificarAutor(objeto, usuarioId);
        if (objeto.getEstado() == EstadoObjeto.ELIMINADO) {
            throw new ResourceNotFoundException("Objeto no encontrado con id " + id);
        }
        objeto.setEstado(EstadoObjeto.ELIMINADO);
        objetoRepository.save(objeto);
    }

    @Transactional(readOnly = true)
    public List<ObjetoResponse> porCategoria(Long categoriaId) {
        validarCategoria(categoriaId);
        return objetoRepository.findByCategoriaIdAndEstadoInOrderByFechaPublicacionDescIdDesc(
                        categoriaId, ESTADOS_PUBLICOS)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ObjetoResponse> porUsuario(Long usuarioId) {
        return objetoRepository.findByUsuarioIdOrderByFechaPublicacionDesc(usuarioId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ObjetoResponse toResponse(Objeto objeto) {
        return new ObjetoResponse(
                objeto.getId(),
                objeto.getNombre(),
                objeto.getDescripcion(),
                objeto.getUbicacion(),
                objeto.getLatitud(),
                objeto.getLongitud(),
                objeto.getFechaObjeto(),
                objeto.getTipo().name(),
                objeto.getEstado().name(),
                objeto.getFechaPublicacion(),
                new CategoriaResumen(objeto.getCategoria().getId(), objeto.getCategoria().getNombre()),
                // H19: el autor se expone solo con su vista pública (sin correo ni apellido completo).
                UsuarioResumen.publico(objeto.getUsuario()),
                objeto.getFotos().stream()
                        .map(foto -> new FotoResponse(foto.getId(), foto.getUrl()))
                        .toList(),
                objeto.getFechaRecuperacion() == null ? null : objeto.getFechaRecuperacion().toInstant(ZoneOffset.UTC));
    }

    private void validarCategoria(Long categoriaId) {
        // Una categoría desactivada sigue sirviendo para consultar publicaciones anteriores.
        if (categoriaId != null && !categoriaRepository.existsById(categoriaId)) {
            throw new ResourceNotFoundException("Categoría no encontrada con id " + categoriaId);
        }
    }

    private void exigirCategoriaActiva(Categoria categoria) {
        if (!Boolean.TRUE.equals(categoria.getEstado())) {
            throw new com.proyectointegrador.exception.SolicitudInvalidaException(
                    "No se puede asignar una categoría desactivada");
        }
    }

    private Objeto buscarPublico(Long id) {
        Objeto objeto = buscarEntidad(id);
        if (!ESTADOS_PUBLICOS.contains(objeto.getEstado())) {
            throw new ResourceNotFoundException("Objeto no encontrado con id " + id);
        }
        return objeto;
    }

    private Objeto buscarEntidad(Long id) {
        return objetoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Objeto no encontrado con id " + id));
    }

    private void verificarAutor(Objeto objeto, Long usuarioId) {
        if (!objeto.getUsuario().getId().equals(usuarioId)) {
            throw new AccessDeniedException("Solo el autor puede administrar esta publicación");
        }
    }

    private void registrarEvento(Objeto objeto) {
        var pendiente=new EventoCoincidencia();pendiente.setObjeto(objeto);
        eventosPendientes.saveAndFlush(pendiente);
        eventos.publishEvent(new ObjetoGuardado(objeto.getId()));
    }

    private Objeto buscarParaActualizar(Long id) {
        return objetoRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Objeto no encontrado con id " + id));
    }
}
