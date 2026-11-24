package com.proyectointegrador.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyectointegrador.exception.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.LongSupplier;

/**
 * H70: mientras el panel restaura una versión anterior (restauración SOLICITADA o EN_PROCESO), la API
 * rechaza las escrituras con 503: lo que se escribiera se perdería al reemplazar la base. Las lecturas y el
 * inicio de sesión siguen. Si la base no responde y hubo mantenimiento hace poco (la restauración la
 * reemplaza unos segundos), también responde 503.
 */
@Component
public class ModoMantenimientoFiltro extends OncePerRequestFilter {

    static final String MENSAJE = "FoundIA está restaurando una versión anterior de los datos. Intenta de nuevo en unos minutos.";
    private static final Set<String> ESCRITURAS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private static final long VIGENCIA_MS = 2_000;
    private static final long RECUERDA_MANTENIMIENTO_MS = 120_000;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final LongSupplier reloj;
    private volatile long consultadoEn = Long.MIN_VALUE / 2;
    private volatile boolean enMantenimiento;
    private volatile long ultimoMantenimiento = Long.MIN_VALUE / 2;

    @org.springframework.beans.factory.annotation.Autowired
    public ModoMantenimientoFiltro(JdbcTemplate jdbc, ObjectMapper json) {
        this(jdbc, json, System::currentTimeMillis);
    }

    ModoMantenimientoFiltro(JdbcTemplate jdbc, ObjectMapper json, LongSupplier reloj) {
        this.jdbc = jdbc;
        this.json = json;
        this.reloj = reloj;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        return !ESCRITURAS.contains(request.getMethod()) || !ruta.startsWith("/api/")
                || ruta.equals("/api/auth/login");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!hayMantenimiento()) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        response.setHeader("Retry-After", "60");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(response.getOutputStream(),
                ApiError.of(HttpStatus.SERVICE_UNAVAILABLE, MENSAJE, request.getRequestURI()));
    }

    boolean hayMantenimiento() {
        long ahora = reloj.getAsLong();
        if (ahora - consultadoEn < VIGENCIA_MS) return enMantenimiento;
        boolean valor;
        try {
            Integer abiertas = jdbc.queryForObject("SELECT COUNT(*) FROM restauraciones WHERE modo = 'APLICADA' "
                    + "AND estado IN ('SOLICITADA', 'EN_PROCESO')", Integer.class);
            valor = abiertas != null && abiertas > 0;
        } catch (BadSqlGrammarException sinTabla) {
            // Sin la tabla (base sin H70) no hay restauraciones; durante el reemplazo, sí puede faltar unos segundos
            valor = ahora - ultimoMantenimiento < RECUERDA_MANTENIMIENTO_MS;
        } catch (DataAccessException sinBase) {
            valor = ahora - ultimoMantenimiento < RECUERDA_MANTENIMIENTO_MS;
        }
        if (valor) ultimoMantenimiento = ahora;
        enMantenimiento = valor;
        consultadoEn = ahora;
        return valor;
    }
}
