package com.proyectointegrador.service;

import com.proyectointegrador.exception.SolicitudInvalidaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class InvitacionService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JdbcTemplate jdbc;
    private final String webUrl;

    public InvitacionService(JdbcTemplate jdbc, @Value("${app.web.url:http://localhost:5173}") String webUrl) {
        this.jdbc = jdbc;
        this.webUrl = webUrl.replaceFirst("/+$", "");
    }

    public String crear(Long usuarioId) {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String codigo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update("INSERT INTO invitaciones (codigo_hash, usuario_id) VALUES (?, ?)", huella(codigo), usuarioId);
        return webUrl + "/registro?invitacion=" + codigo;
    }

    public void registrarUso(String codigo) {
        if (!codigo.matches("[A-Za-z0-9_-]{32}") || jdbc.update(
                "UPDATE invitaciones SET usos = usos + 1 WHERE codigo_hash = ?", huella(codigo)) != 1) {
            throw new SolicitudInvalidaException("La invitación no es válida. Puedes registrarte sin el enlace.");
        }
    }

    private static String huella(String codigo) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(codigo.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
