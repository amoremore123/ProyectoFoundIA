package com.proyectointegrador.service;

import com.proyectointegrador.dto.FiltroBusqueda;
import com.proyectointegrador.dto.ObjetoResponse;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.exception.SolicitudInvalidaException;
import com.proyectointegrador.repository.CategoriaRepository;
import com.proyectointegrador.repository.ObjetoRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class BusquedaService {

    private static final double RADIO_PREDETERMINADO_KM = 5;
    private static final double RADIO_MAXIMO_KM = 1000;
    private static final double RADIO_TERRESTRE_KM = 6371;

    private final ObjetoRepository objetoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ObjetoService objetoService;

    public BusquedaService(ObjetoRepository objetoRepository, CategoriaRepository categoriaRepository,
                           ObjetoService objetoService) {
        this.objetoRepository = objetoRepository;
        this.categoriaRepository = categoriaRepository;
        this.objetoService = objetoService;
    }

    @Transactional(readOnly = true)
    public List<ObjetoResponse> buscar(FiltroBusqueda f) {
        validar(f);
        if (f.categoriaId() != null && !categoriaRepository.existsById(f.categoriaId())) {
            throw new ResourceNotFoundException("Categoría no encontrada con id " + f.categoriaId());
        }

        Specification<Objeto> filtros = (root, query, cb) -> {
            var condiciones = new ArrayList<Predicate>();
            condiciones.add(root.get("estado").in(EstadoObjeto.ACTIVO, EstadoObjeto.RECUPERADO));
            if (f.q() != null && !f.q().isBlank()) {
                String texto = like(f.q());
                condiciones.add(cb.or(
                        cb.like(cb.lower(root.get("nombre")), texto, '!'),
                        cb.like(cb.lower(root.get("descripcion")), texto, '!'),
                        cb.like(cb.lower(root.get("ubicacion")), texto, '!')));
            }
            if (f.tipo() != null) {
                condiciones.add(cb.equal(root.get("tipo"), f.tipo()));
            }
            if (f.categoriaId() != null) {
                condiciones.add(cb.equal(root.get("categoria").get("id"), f.categoriaId()));
            }
            if (f.ubicacion() != null && !f.ubicacion().isBlank()) {
                condiciones.add(cb.like(cb.lower(root.get("ubicacion")), like(f.ubicacion()), '!'));
            }

            var orden = new ArrayList<Order>();
            if (f.latitud() != null) {
                condiciones.add(cb.isNotNull(root.get("latitud")));
                condiciones.add(cb.isNotNull(root.get("longitud")));
                Expression<Double> distancia = distancia(cb, root, f.latitud(), f.longitud());
                condiciones.add(cb.le(distancia, f.radioKm() == null ? RADIO_PREDETERMINADO_KM : f.radioKm()));
                orden.add(cb.asc(distancia));
            }
            orden.add(cb.desc(root.get("fechaPublicacion")));
            orden.add(cb.desc(root.get("id")));
            query.orderBy(orden);
            return cb.and(condiciones.toArray(Predicate[]::new));
        };
        return objetoRepository.findAll(filtros).stream().map(objetoService::toResponse).toList();
    }

    private void validar(FiltroBusqueda f) {
        if ((f.latitud() == null) != (f.longitud() == null)) {
            throw new SolicitudInvalidaException("Indica latitud y longitud juntas");
        }
        if (f.latitud() != null && (!Double.isFinite(f.latitud()) || Math.abs(f.latitud()) > 90
                || !Double.isFinite(f.longitud()) || Math.abs(f.longitud()) > 180)) {
            throw new SolicitudInvalidaException("Coordenadas fuera de rango");
        }
        if (f.radioKm() != null && (f.latitud() == null || !Double.isFinite(f.radioKm())
                || f.radioKm() <= 0 || f.radioKm() > RADIO_MAXIMO_KM)) {
            throw new SolicitudInvalidaException("El radio requiere coordenadas y debe ser mayor que 0 y hasta 1000 km");
        }
    }

    private String like(String valor) {
        return "%" + valor.trim().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    // Haversine en SQL: filtra y ordena en la base, no tras descargar resultados.
    private Expression<Double> distancia(CriteriaBuilder cb, Root<Objeto> objeto, double latitud, double longitud) {
        double radianes = Math.PI / 180;
        Expression<Double> dLat = cb.prod(cb.diff(objeto.get("latitud").as(Double.class), latitud), radianes / 2);
        Expression<Double> dLon = cb.prod(cb.diff(objeto.get("longitud").as(Double.class), longitud), radianes / 2);
        Expression<Double> cosLat = cb.function("cos", Double.class,
                cb.prod(objeto.get("latitud").as(Double.class), radianes));
        Expression<Double> a = cb.sum(cuadrado(cb, cb.function("sin", Double.class, dLat)),
                cb.prod(cb.prod(cb.literal(Math.cos(latitud * radianes)), cosLat),
                        cuadrado(cb, cb.function("sin", Double.class, dLon))));
        // El redondeo no debe sacar asin/sqrt de su dominio (puntos iguales o antípodas).
        Expression<Double> limitado = cb.function("least", Double.class, cb.literal(1d),
                cb.function("greatest", Double.class, cb.literal(0d), a));
        return cb.prod(cb.function("asin", Double.class, cb.function("sqrt", Double.class, limitado)),
                2 * RADIO_TERRESTRE_KM);
    }

    private Expression<Double> cuadrado(CriteriaBuilder cb, Expression<Double> valor) {
        return cb.prod(valor, valor);
    }
}
