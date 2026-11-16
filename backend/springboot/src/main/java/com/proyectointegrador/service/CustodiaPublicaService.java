package com.proyectointegrador.service;

import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.repository.ObjetoRepository;
import com.proyectointegrador.security.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;

/**
 * H41: lo que ve cualquiera que escanea la etiqueta de custodia. Solo datos del objeto
 * y de la custodia: nunca quién lo dejó, quién lo recibió ni quién lo reclamó.
 */
@Service
public class CustodiaPublicaService {

    private static final String NO_VALIDA = "La etiqueta no es válida o ya no corresponde a una custodia";

    private final JwtService jwtService;
    private final JdbcTemplate jdbc;
    private final ObjetoRepository objetoRepository;

    public CustodiaPublicaService(JwtService jwtService, JdbcTemplate jdbc, ObjetoRepository objetoRepository) {
        this.jwtService = jwtService;
        this.jdbc = jdbc;
        this.objetoRepository = objetoRepository;
    }

    public record ObjetoPublico(Long id, String nombre, String categoria, String fotoUrl) {}

    public record CustodiaPublica(String codigo, String estado, LocalDateTime fechaRecepcion, ObjetoPublico objeto) {}

    @Transactional(readOnly = true)
    public CustodiaPublica verificar(String etiqueta) {
        String token = extraerToken(etiqueta);
        Claims datos;
        try {
            datos = jwtService.leerTokenCustodia(token);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ResourceNotFoundException(NO_VALIDA);
        }
        Number objetoId = datos.get("objeto", Number.class);
        if (objetoId == null) throw new ResourceNotFoundException(NO_VALIDA);

        var filas = jdbc.query(
                "select codigo, estado, token_qr, fecha_recepcion from custodias where codigo = ? and objeto_id = ?",
                (rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toLocalDateTime()},
                datos.getSubject(), objetoId.longValue());
        // Si la etiqueta se volvió a emitir, la anterior deja de servir
        if (filas.isEmpty() || !iguales((String) filas.get(0)[2], token)) {
            throw new ResourceNotFoundException(NO_VALIDA);
        }
        Object[] fila = filas.get(0);
        var objeto = objetoRepository.findById(objetoId.longValue())
                .orElseThrow(() -> new ResourceNotFoundException(NO_VALIDA));
        String foto = objeto.getFotos() == null || objeto.getFotos().isEmpty() ? null : objeto.getFotos().get(0).getUrl();
        String categoria = objeto.getCategoria() == null ? null : objeto.getCategoria().getNombre();
        return new CustodiaPublica((String) fila[0], (String) fila[1], (LocalDateTime) fila[3],
                new ObjetoPublico(objeto.getId(), objeto.getNombre(), categoria, foto));
    }

    /** La etiqueta puede llegar como el token solo o como el enlace que lleva el QR (…?t=token). */
    public static String extraerToken(String etiqueta) {
        String valor = etiqueta == null ? "" : etiqueta.strip();
        if (valor.startsWith("http://") || valor.startsWith("https://")) {
            try {
                String consulta = URI.create(valor).getRawQuery();
                if (consulta != null) {
                    for (String parte : consulta.split("&")) {
                        if (parte.startsWith("t=")) return URLDecoder.decode(parte.substring(2), StandardCharsets.UTF_8);
                    }
                }
            } catch (IllegalArgumentException ignorado) {
                // enlace mal formado: se trata como token y fallará la firma
            }
            return "";
        }
        return valor;
    }

    private static boolean iguales(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
