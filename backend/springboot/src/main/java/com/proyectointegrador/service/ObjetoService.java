package com.proyectointegrador.service;

import com.proyectointegrador.dto.CoincidenciaSugeridaResponse;
import com.proyectointegrador.dto.FotoResponse;
import com.proyectointegrador.dto.ObjetoRequest;
import com.proyectointegrador.dto.ObjetoResponse;
import com.proyectointegrador.dto.CategoriaResumen;
import com.proyectointegrador.dto.UsuarioResumen;
import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.repository.CategoriaRepository;
import com.proyectointegrador.repository.ObjetoRepository;
import com.proyectointegrador.repository.UsuarioRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class ObjetoService {

    private static final BigDecimal PORCENTAJE_ALTA = new BigDecimal("90.00");
    private static final BigDecimal PORCENTAJE_MEDIA = new BigDecimal("65.00");
    private static final List<EstadoObjeto> ESTADOS_PUBLICOS = List.of(EstadoObjeto.ACTIVO, EstadoObjeto.RECUPERADO);
    private static final Sort ORDEN_PUBLICACION = Sort.by(Sort.Direction.DESC, "fechaPublicacion", "id");

    private final ObjetoRepository objetoRepository;
    private final CategoriaRepository categoriaRepository;
    private final UsuarioRepository usuarioRepository;

    public ObjetoService(ObjetoRepository objetoRepository,
                         CategoriaRepository categoriaRepository,
                         UsuarioRepository usuarioRepository) {
        this.objetoRepository = objetoRepository;
        this.categoriaRepository = categoriaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    @Transactional(readOnly = true)
    public List<ObjetoResponse> listar(TipoObjeto tipo, EstadoObjeto estado, Long categoriaId) {
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
        return toResponse(objeto);
    }

    @Transactional
    public ObjetoResponse actualizar(Long id, ObjetoRequest request, Long usuarioId, Rol rolActual) {
        Objeto objeto = buscarEntidad(id);
        verificarPermiso(objeto, usuarioId, rolActual);

        Categoria categoria = categoriaRepository.findById(request.categoriaId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada con id " + request.categoriaId()));

        objeto.setCategoria(categoria);
        objeto.setNombre(request.nombre());
        objeto.setDescripcion(request.descripcion());
        objeto.setFechaObjeto(request.fechaObjeto());
        objeto.setTipo(request.tipo());
        objeto.setUbicacion(request.ubicacion());
        objeto.setLatitud(request.latitud());
        objeto.setLongitud(request.longitud());

        objetoRepository.save(objeto);
        return toResponse(objeto);
    }

    @Transactional
    public void eliminar(Long id, Long usuarioId, Rol rolActual) {
        Objeto objeto = buscarEntidad(id);
        verificarPermiso(objeto, usuarioId, rolActual);
        objeto.setEstado(EstadoObjeto.ELIMINADO);
        objetoRepository.save(objeto);
    }

    @Transactional(readOnly = true)
    public List<ObjetoResponse> buscar(String texto, TipoObjeto tipo, Long categoriaId) {
        Specification<Objeto> specification =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(root.get("estado").in(ESTADOS_PUBLICOS));
                    if (texto != null && !texto.isBlank()) {
                        String like = "%" + escaparTextoLike(texto.trim().toLowerCase(Locale.ROOT)) + "%";
                        predicates.add(cb.or(
                                cb.like(cb.lower(root.get("nombre")), like, '!'),
                                cb.like(cb.lower(root.get("descripcion")), like, '!'),
                                cb.like(cb.lower(root.get("ubicacion")), like, '!')));
                    }
                    if (tipo != null) {
                        predicates.add(cb.equal(root.get("tipo"), tipo));
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
    public List<ObjetoResponse> porCategoria(Long categoriaId) {
        if (!categoriaRepository.existsById(categoriaId)) {
            throw new ResourceNotFoundException("Categoría no encontrada con id " + categoriaId);
        }
        return objetoRepository.findByCategoriaIdAndEstadoInOrderByFechaPublicacionDescIdDesc(
                        categoriaId, ESTADOS_PUBLICOS)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ObjetoResponse> porUbicacion(String ubicacion) {
        String texto = ubicacion == null ? "" : ubicacion.trim();
        return objetoRepository.findByUbicacionContainingIgnoreCaseAndEstadoInOrderByFechaPublicacionDescIdDesc(
                        texto, ESTADOS_PUBLICOS)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ObjetoResponse> porFecha(LocalDate desde, LocalDate hasta) {
        Specification<Objeto> specification =
                (root, query, cb) -> {
                    List<Predicate> predicates = new ArrayList<>();
                    predicates.add(root.get("estado").in(ESTADOS_PUBLICOS));
                    if (desde != null) {
                        predicates.add(cb.greaterThanOrEqualTo(root.get("fechaObjeto"), desde));
                    }
                    if (hasta != null) {
                        predicates.add(cb.lessThanOrEqualTo(root.get("fechaObjeto"), hasta));
                    }
                    return cb.and(predicates.toArray(new Predicate[0]));
                };

        return objetoRepository.findAll(specification, ORDEN_PUBLICACION)
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
    public List<CoincidenciaSugeridaResponse> coincidencias(Long id) {
        Objeto base = buscarPublico(id);
        TipoObjeto tipoOpuesto = base.getTipo() == TipoObjeto.PERDIDO
                ? TipoObjeto.ENCONTRADO
                : TipoObjeto.PERDIDO;

        List<Objeto> candidatos = objetoRepository
                .findByTipoAndCategoriaIdAndEstado(tipoOpuesto, base.getCategoria().getId(), EstadoObjeto.ACTIVO)
                .stream()
                .filter(objeto -> !objeto.getId().equals(base.getId()))
                .sorted(Comparator.comparing(Objeto::getFechaPublicacion,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return candidatos.stream()
                .map(objeto -> {
                    boolean coincideUbicacion = ubicacionCoincide(base.getUbicacion(), objeto.getUbicacion());
                    return new CoincidenciaSugeridaResponse(
                            objeto.getId(),
                            objeto.getNombre(),
                            objeto.getTipo().name(),
                            objeto.getCategoria().getNombre(),
                            objeto.getUbicacion(),
                            objeto.getFechaObjeto(),
                            coincideUbicacion ? PORCENTAJE_ALTA : PORCENTAJE_MEDIA,
                            coincideUbicacion ? "ALTA" : "MEDIA");
                })
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
                new UsuarioResumen(
                        objeto.getUsuario().getId(),
                        objeto.getUsuario().getNombre(),
                        objeto.getUsuario().getApellido()),
                objeto.getFotos().stream()
                        .map(foto -> new FotoResponse(foto.getId(), foto.getUrl()))
                        .toList());
    }

    private String escaparTextoLike(String texto) {
        // El texto del usuario es literal: % y _ no deben convertirse en comodines SQL.
        return texto.replace("!", "!!").replace("%", "!%").replace("_", "!_");
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

    private void verificarPermiso(Objeto objeto, Long usuarioId, Rol rolActual) {
        boolean esDuenio = objeto.getUsuario().getId().equals(usuarioId);
        boolean esAdmin = rolActual == Rol.ADMIN;
        if (!esDuenio && !esAdmin) {
            throw new AccessDeniedException("No tienes permiso sobre este objeto");
        }
    }

    private boolean ubicacionCoincide(String una, String otra) {
        if (una == null || una.isBlank() || otra == null || otra.isBlank()) {
            return false;
        }
        return una.trim().equalsIgnoreCase(otra.trim());
    }
}
