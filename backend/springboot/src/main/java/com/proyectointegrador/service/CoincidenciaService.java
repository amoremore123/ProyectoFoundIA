package com.proyectointegrador.service;

import com.proyectointegrador.dto.CoincidenciaDetalleResponse;
import com.proyectointegrador.dto.CoincidenciaSugeridaResponse;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.repository.CoincidenciaRepository;
import com.proyectointegrador.repository.ObjetoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CoincidenciaService {

    public static final int UMBRAL_SIMILITUD = 40;

    private final ObjetoRepository objetoRepository;
    private final CoincidenciaRepository coincidenciaRepository;
    private final ObjetoService objetoService;

    public CoincidenciaService(ObjetoRepository objetoRepository, CoincidenciaRepository coincidenciaRepository,
                               ObjetoService objetoService) {
        this.objetoRepository = objetoRepository;
        this.coincidenciaRepository = coincidenciaRepository;
        this.objetoService = objetoService;
    }

    @Transactional(readOnly = true)
    public List<CoincidenciaSugeridaResponse> listar(Long id) {
        Objeto base = publico(id);
        if (base.getEstado() != EstadoObjeto.ACTIVO) {
            return List.of();
        }
        TipoObjeto opuesto = base.getTipo() == TipoObjeto.PERDIDO ? TipoObjeto.ENCONTRADO : TipoObjeto.PERDIDO;
        // Consulta acotada por tipo/categoría/estado, sin cargar todo el catálogo.
        return objetoRepository.findByTipoAndCategoriaIdAndEstado(opuesto, base.getCategoria().getId(), EstadoObjeto.ACTIVO)
                .stream()
                .filter(objeto -> !objeto.getUsuario().getId().equals(base.getUsuario().getId()))
                .map(objeto -> sugerencia(base, objeto))
                .filter(sugerencia -> sugerencia.porcentaje().intValue() >= UMBRAL_SIMILITUD)
                .sorted(Comparator.comparing(CoincidenciaSugeridaResponse::porcentaje).reversed()
                        .thenComparing(CoincidenciaSugeridaResponse::objetoId))
                .toList();
    }

    private CoincidenciaSugeridaResponse sugerencia(Objeto base, Objeto candidato) {
        int total = puntuar(base, candidato);
        String clave = base.getTipo() == TipoObjeto.PERDIDO
                ? base.getId() + "-" + candidato.getId()
                : candidato.getId() + "-" + base.getId();
        return new CoincidenciaSugeridaResponse(candidato.getId(), candidato.getNombre(), candidato.getTipo().name(),
                candidato.getCategoria().getNombre(), candidato.getUbicacion(), candidato.getFechaObjeto(),
                BigDecimal.valueOf(total), nivel(total), clave);
    }

    @Transactional(readOnly = true)
    public CoincidenciaDetalleResponse detalle(String id) {
        long[] par = identificar(id);
        Objeto perdido = publico(par[0]);
        Objeto encontrado = publico(par[1]);
        if (perdido.getEstado() != EstadoObjeto.ACTIVO || encontrado.getEstado() != EstadoObjeto.ACTIVO
                || perdido.getTipo() != TipoObjeto.PERDIDO || encontrado.getTipo() != TipoObjeto.ENCONTRADO
                || !perdido.getCategoria().getId().equals(encontrado.getCategoria().getId())
                || perdido.getUsuario().getId().equals(encontrado.getUsuario().getId())) {
            throw noDisponible();
        }
        Map<String, Integer> puntos = criterios(perdido, encontrado);
        int total = puntos.values().stream().mapToInt(Integer::intValue).sum();
        if (total < UMBRAL_SIMILITUD) {
            throw noDisponible();
        }
        return new CoincidenciaDetalleResponse(perdido.getId() + "-" + encontrado.getId(),
                objetoService.toResponse(perdido), objetoService.toResponse(encontrado),
                BigDecimal.valueOf(total), nivel(total), puntos);
    }

    private long[] identificar(String id) {
        try {
            if (id.matches("[1-9][0-9]*-[1-9][0-9]*")) {
                String[] partes = id.split("-");
                return new long[]{Long.parseLong(partes[0]), Long.parseLong(partes[1])};
            }
            if (!id.matches("[1-9][0-9]*")) {
                throw noDisponible();
            }
            // Compatibilidad con coincidencias ya persistidas en el esquema compartido.
            var coincidencia = coincidenciaRepository.findById(Long.parseLong(id)).orElseThrow(this::noDisponible);
            return new long[]{coincidencia.getObjetoPerdido().getId(), coincidencia.getObjetoEncontrado().getId()};
        } catch (NumberFormatException ex) {
            throw noDisponible();
        }
    }

    private Objeto publico(Long id) {
        Objeto objeto = objetoRepository.findById(id).orElseThrow(this::noDisponible);
        if (objeto.getEstado() != EstadoObjeto.ACTIVO && objeto.getEstado() != EstadoObjeto.RECUPERADO) {
            throw noDisponible();
        }
        return objeto;
    }

    private ResourceNotFoundException noDisponible() {
        return new ResourceNotFoundException("Coincidencia no disponible");
    }

    // Compartidos para que H18 pueda integrar el mismo motor, sin duplicar porcentajes.
    public int puntuar(Objeto primero, Objeto segundo) {
        return criterios(primero, segundo).values().stream().mapToInt(Integer::intValue).sum();
    }

    public String nivel(int total) {
        return total >= 80 ? "ALTA" : total >= 55 ? "MEDIA" : "BAJA";
    }

    public Map<String, Integer> criterios(Objeto primero, Objeto segundo) {
        Set<String> uno = tokens(primero.getNombre());
        Set<String> dos = tokens(segundo.getNombre());
        var union = new HashSet<>(uno);
        union.addAll(dos);
        var interseccion = new HashSet<>(uno);
        interseccion.retainAll(dos);
        int nombre = union.isEmpty() ? 0 : (int) Math.round(40d * interseccion.size() / union.size());

        String lugarA = normalizar(primero.getUbicacion());
        String lugarB = normalizar(segundo.getUbicacion());
        int ubicacion = lugarA.isBlank() || lugarB.isBlank() ? 0 : lugarA.equals(lugarB) ? 20
                : lugarA.contains(lugarB) || lugarB.contains(lugarA) ? 10 : 0;
        long dias = primero.getFechaObjeto() == null || segundo.getFechaObjeto() == null ? Long.MAX_VALUE
                : Math.abs(ChronoUnit.DAYS.between(primero.getFechaObjeto(), segundo.getFechaObjeto()));
        int categoria = primero.getCategoria() != null && segundo.getCategoria() != null
                && primero.getCategoria().getId() != null
                && primero.getCategoria().getId().equals(segundo.getCategoria().getId()) ? 25 : 0;
        return Map.of("nombre", nombre, "categoria", categoria, "ubicacion", ubicacion,
                "fecha", dias <= 3 ? 15 : dias <= 7 ? 10 : dias <= 30 ? 5 : 0);
    }

    private Set<String> tokens(String texto) {
        String limpio = normalizar(texto);
        return limpio.isBlank() ? Set.of() : new HashSet<>(Arrays.asList(limpio.split("\\s+")));
    }

    private String normalizar(String texto) {
        return texto == null ? "" : Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
