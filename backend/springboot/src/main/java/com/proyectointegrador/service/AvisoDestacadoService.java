package com.proyectointegrador.service;

import com.proyectointegrador.dto.AvisoDestacadoResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * H71: los avisos destacados los administra el panel (Django); aquí solo se leen los vigentes.
 * Las fechas son del calendario de Lima (UTC-5, sin horario de verano), como en Django.
 */
@Service
public class AvisoDestacadoService {

    public static final int MAXIMO = 5;
    static final ZoneOffset LIMA = ZoneOffset.ofHours(-5);

    private final JdbcTemplate jdbc;
    private final Clock reloj;

    @Autowired
    public AvisoDestacadoService(JdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    AvisoDestacadoService(JdbcTemplate jdbc, Clock reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    public List<AvisoDestacadoResponse> vigentes() {
        LocalDate hoy = LocalDate.now(reloj.withZone(LIMA));
        return jdbc.query("""
                SELECT a.id, a.titulo, a.texto, a.destino, a.fecha_fin, c.origen, c.destino AS destino_campana
                  FROM avisos_destacados a LEFT JOIN campanas c ON c.id = a.campana_id
                 WHERE a.retirado_en IS NULL AND a.fecha_inicio <= ? AND a.fecha_fin >= ?
                 ORDER BY a.fecha_inicio DESC, a.id DESC
                 LIMIT ?""",
                (rs, i) -> new AvisoDestacadoResponse(rs.getLong("id"), rs.getString("titulo"), rs.getString("texto"),
                        enlace(rs.getString("destino"), rs.getString("origen"), rs.getString("destino_campana")),
                        rs.getDate("fecha_fin").toLocalDate()),
                Date.valueOf(hoy), Date.valueOf(hoy), MAXIMO);
    }

    /** Con campaña, su enlace con {@code ?origen=} para que H67 cuente la visita; si no, la ruta elegida. */
    static String enlace(String destino, String origen, String destinoCampana) {
        if (origen != null) {
            String ruta = destinoCampana == null || destinoCampana.isBlank() ? "/" : destinoCampana;
            return ruta + "?origen=" + URLEncoder.encode(origen, StandardCharsets.UTF_8);
        }
        return destino == null || destino.isBlank() ? null : destino;
    }
}
