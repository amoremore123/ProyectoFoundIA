package com.proyectointegrador.service;

import com.proyectointegrador.dto.RegisterRequest;
import com.proyectointegrador.exception.SolicitudInvalidaException;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Una versión identifica el par de documentos inmutables presentado al registrarse. */
public final class DocumentosLegales {
    public static final String VERSION = "proyecto-2026-10-26-v1";
    public record Documentos(String version, String estado, String terminos, String privacidad, String sha256) {}
    private static final Documentos ACTUALES = cargar();
    private DocumentosLegales() {}

    public static Documentos documentos() { return ACTUALES; }

    public static void validar(RegisterRequest request) {
        if (!Boolean.TRUE.equals(request.aceptaTerminos()))
            throw new SolicitudInvalidaException("Debes aceptar expresamente los términos y la política de privacidad.");
        if (!VERSION.equals(request.versionLegal()))
            throw new SolicitudInvalidaException("La versión de los documentos cambió. Léelos de nuevo antes de registrarte.");
    }

    private static Documentos cargar() {
        try {
            String terminos = leer("terminos");
            String privacidad = leer("privacidad");
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((terminos + "\n" + privacidad).getBytes(StandardCharsets.UTF_8)));
            return new Documentos(VERSION, "TEXTO_DEL_PROYECTO_PENDIENTE_REVISION_INSTITUCIONAL", terminos, privacidad, hash);
        } catch (Exception e) { throw new IllegalStateException("No se pudieron cargar los documentos de registro", e); }
    }

    private static String leer(String nombre) throws java.io.IOException {
        try (var entrada = new ClassPathResource("legal/" + VERSION + "/" + nombre + ".md").getInputStream()) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
