package com.proyectointegrador.service;

import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.repository.ObjetoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * H53: detecta objetos perdidos sin resolver y pide un recordatorio por objeto.
 *
 * <p>Un objeto se considera sin resolver cuando es de tipo {@code PERDIDO},
 * sigue {@code ACTIVO}, no tiene fecha de recuperación y se publicó hace al
 * menos {@code app.recordatorios.dias-sin-resolver} días. El periodo de
 * deduplicación es el mes UTC ({@code yyyy-MM}).</p>
 */
@Service
public class RecordatorioObjetoService {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioObjetoService.class);
    private static final DateTimeFormatter FORMATO_PERIODO = DateTimeFormatter.ofPattern("yyyy-MM");

    private final ObjetoRepository objetos;
    private final RecordatorioObjetoGenerador generador;
    private final long diasSinResolver;
    private final Clock clock;

    @Autowired
    public RecordatorioObjetoService(ObjetoRepository objetos,
                                     RecordatorioObjetoGenerador generador,
                                     @Value("${app.recordatorios.dias-sin-resolver:7}") long diasSinResolver) {
        this(objetos, generador, diasSinResolver, Clock.systemUTC());
    }

    /** Constructor para pruebas: permite fijar la hora. */
    RecordatorioObjetoService(ObjetoRepository objetos, RecordatorioObjetoGenerador generador,
                              long diasSinResolver, Clock clock) {
        this.objetos = objetos;
        this.generador = generador;
        this.diasSinResolver = diasSinResolver;
        this.clock = clock;
    }

    /**
     * Ejecuta una detección con la hora actual y devuelve cuántos recordatorios
     * nuevos se crearon.
     */
    public int ejecutar() {
        LocalDateTime ahora = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        return ejecutar(ahora);
    }

    int ejecutar(LocalDateTime ahora) {
        if (diasSinResolver < 1) {
            log.warn("app.recordatorios.dias-sin-resolver debe ser al menos 1; no se generan recordatorios.");
            return 0;
        }
        LocalDateTime limite = ahora.minusDays(diasSinResolver);
        String periodo = FORMATO_PERIODO.format(ahora);
        List<Objeto> pendientes = objetos
                .findByTipoAndEstadoAndFechaRecuperacionIsNullAndFechaPublicacionLessThanEqualOrderById(
                        TipoObjeto.PERDIDO, EstadoObjeto.ACTIVO, limite);

        int creados = 0;
        for (Objeto objeto : pendientes) {
            try {
                if (generador.generar(objeto.getId(), periodo, limite)) {
                    creados++;
                }
            } catch (DataIntegrityViolationException e) {
                // Otra ejecución ganó la restricción única (objeto_id, periodo).
                log.debug("Recordatorio ya registrado para el objeto {} en el periodo {}",
                        objeto.getId(), periodo);
            } catch (RuntimeException e) {
                log.warn("No se pudo generar el recordatorio del objeto {}: {}",
                        objeto.getId(), e.getMessage());
            }
        }
        if (creados > 0) {
            log.info("Recordatorios de objetos sin resolver generados: {} (periodo {})", creados, periodo);
        }
        return creados;
    }
}
