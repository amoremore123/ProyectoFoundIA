package com.proyectointegrador.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * H62: al arrancar, mueve a la carpeta privada {@code chat/} las fotos del chat que se guardaron antes en
 * la carpeta pública {@code /uploads/} y actualiza su ruta. Es idempotente: cuando no queda ninguna, no
 * hace nada. Nunca impide que la aplicación arranque.
 */
@Component
public class MigradorFotosChat implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MigradorFotosChat.class);

    private final JdbcTemplate jdbc;
    private final AlmacenamientoImagenService imagenes;

    public MigradorFotosChat(JdbcTemplate jdbc, AlmacenamientoImagenService imagenes) {
        this.jdbc = jdbc;
        this.imagenes = imagenes;
    }

    @Override
    public void run(ApplicationArguments args) {
        migrar();
    }

    /** @return cuántas fotos se pasaron a la carpeta privada */
    public int migrar() {
        List<Map<String, Object>> pendientes;
        try {
            pendientes = jdbc.queryForList(
                    "SELECT id, imagen_url FROM mensajes_chat WHERE imagen_url LIKE '/uploads/%'");
        } catch (DataAccessException sinTabla) {
            // Base sin el chat (H55): no hay nada que mover
            return 0;
        }
        int movidas = 0;
        for (Map<String, Object> fila : pendientes) {
            Object id = fila.get("id");
            String ruta = (String) fila.get("imagen_url");
            try {
                String nueva = imagenes.moverAChat(ruta);
                jdbc.update("UPDATE mensajes_chat SET imagen_url = ? WHERE id = ? AND imagen_url = ?", nueva, id, ruta);
                movidas++;
            } catch (IllegalArgumentException | IOException | DataAccessException ex) {
                log.warn("No se pudo pasar a privada la foto del mensaje {}: {}", id, ex.getClass().getSimpleName());
            }
        }
        if (movidas > 0) log.info("Fotos del chat pasadas a la carpeta privada: {}", movidas);
        return movidas;
    }
}
