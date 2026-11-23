package com.proyectointegrador.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Reconocimiento a quien devuelve. Solo cuenta lo que la biblioteca verificó, para que no se pueda
 * inflar con publicaciones falsas: los objetos que la persona encontró y entregó en custodia (la
 * biblioteca los recibió en mano) y, de ellos, los que su dueño ya retiró con el QR. Es privado:
 * solo lo ve la propia persona, no hay ranking público.
 */
@Service
public class ReconocimientoService {

    public record Nivel(int minimo, String nombre, String descripcion) {}

    public static final List<Nivel> NIVELES = List.of(
            new Nivel(1, "Primera entrega", "Entregaste tu primer objeto en la biblioteca."),
            new Nivel(3, "Buen compañero", "Ya entregaste 3 objetos: tu ayuda se nota."),
            new Nivel(5, "Guardián de Tecsup", "5 objetos entregados en la biblioteca."),
            new Nivel(10, "Leyenda FoundIA", "10 objetos entregados: eres un ejemplo para la comunidad."));

    public record Siguiente(String nombre, int faltan) {}

    public record Reconocimiento(int entregados, int devueltos, Nivel nivel, Siguiente siguiente) {}

    private final JdbcTemplate jdbc;

    public ReconocimientoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Reconocimiento de(Long usuarioId) {
        // La tabla de custodias la administra el panel (Django); aquí solo se cuenta
        Integer entregados = jdbc.queryForObject(
                "select count(*) from custodias c join objetos o on o.id = c.objeto_id "
                        + "where o.usuario_id = ? and o.tipo = 'ENCONTRADO'", Integer.class, usuarioId);
        Integer devueltos = jdbc.queryForObject(
                "select count(*) from custodias c join objetos o on o.id = c.objeto_id "
                        + "where o.usuario_id = ? and o.tipo = 'ENCONTRADO' and c.estado = 'RETIRADO'", Integer.class, usuarioId);
        return calcular(entregados == null ? 0 : entregados, devueltos == null ? 0 : devueltos);
    }

    public static Reconocimiento calcular(int entregados, int devueltos) {
        Nivel nivel = null;
        Siguiente siguiente = null;
        for (Nivel n : NIVELES) {
            if (entregados >= n.minimo()) {
                nivel = n;
            } else {
                siguiente = new Siguiente(n.nombre(), n.minimo() - entregados);
                break;
            }
        }
        return new Reconocimiento(entregados, devueltos, nivel, siguiente);
    }
}
