package com.proyectointegrador.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * H34: borra del volumen las fotos de las cuentas eliminadas. Django (que atiende la solicitud) no tiene
 * acceso al volumen: las anota en {@code archivos_por_borrar} en la misma transacción y este proceso las
 * borra cada minuto. Si un archivo no se pudo borrar, la fila queda para la siguiente vuelta.
 */
@Service
public class LimpiadorArchivosEliminados {

    private static final Logger log = LoggerFactory.getLogger(LimpiadorArchivosEliminados.class);
    private static final int LOTE = 200;

    private final JdbcTemplate jdbc;
    private final AlmacenamientoImagenService imagenes;

    public LimpiadorArchivosEliminados(JdbcTemplate jdbc, AlmacenamientoImagenService imagenes) {
        this.jdbc = jdbc;
        this.imagenes = imagenes;
    }

    @Scheduled(fixedDelayString = "${app.eliminacion.intervalo-ms:60000}",
               initialDelayString = "${app.eliminacion.retraso-inicial-ms:30000}")
    public void programado() {
        try {
            limpiar();
        } catch (DataAccessException sinTabla) {
            // Base sin la migración de H34: no hay nada que borrar
        }
    }

    /** @return cuántas fotos anotadas se procesaron */
    public int limpiar() {
        List<Map<String, Object>> pendientes = jdbc.queryForList(
                "SELECT id, ruta FROM archivos_por_borrar ORDER BY id LIMIT ?", LOTE);
        int hechas = 0;
        for (Map<String, Object> fila : pendientes) {
            String ruta = (String) fila.get("ruta");
            if (!imagenes.borrarFotoAnotada(ruta)) {
                log.warn("Ruta anotada para borrar no válida (se descarta): fila {}", fila.get("id"));
            } else if (imagenes.archivoChat(ruta).isPresent() || imagenes.archivoPerfil(ruta).isPresent()) {
                continue;  // el archivo sigue ahí: se reintenta en la siguiente vuelta
            }
            jdbc.update("DELETE FROM archivos_por_borrar WHERE id = ?", fila.get("id"));
            hechas++;
        }
        if (hechas > 0) log.info("Fotos de cuentas eliminadas borradas: {}", hechas);
        return hechas;
    }
}
