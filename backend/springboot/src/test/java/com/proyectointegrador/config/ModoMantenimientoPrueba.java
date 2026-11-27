package com.proyectointegrador.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.function.LongSupplier;

/** Acceso de prueba al constructor con reloj del filtro de mantenimiento (H70). */
public final class ModoMantenimientoPrueba {

    private ModoMantenimientoPrueba() {
    }

    public static ModoMantenimientoFiltro nuevo(JdbcTemplate jdbc, ObjectMapper json, LongSupplier reloj) {
        return new ModoMantenimientoFiltro(jdbc, json, reloj);
    }

    public static boolean hay(ModoMantenimientoFiltro filtro) {
        return filtro.hayMantenimiento();
    }
}
