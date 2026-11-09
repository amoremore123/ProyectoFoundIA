package com.proyectointegrador.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyectointegrador.dto.RegisterRequest;
import com.proyectointegrador.exception.SolicitudInvalidaException;
import org.springframework.core.io.ClassPathResource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Una versión identifica el par de documentos inmutables presentado al registrarse. */
public final class DocumentosLegales {
    public static final String VERSION = "foundia-tecsup-2026-11-05-v2";
    public static final String AVISO = "FoundIA es un proyecto académico. Las fuentes oficiales de Tecsup son referencias institucionales; esta casilla acepta solo las condiciones de FoundIA, no autoriza publicidad ni acredita aprobación institucional del proyecto.";
    public record Fuente(String id, String titulo, String url, String consultadoEl,
                         String sha256Original, String sha256Texto) {}
    public record Documentos(String version, String estado, String terminos, String privacidad, String sha256,
                             String aviso, List<Fuente> fuentes) {}
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
            List<Fuente> fuentes = List.of(new ObjectMapper().readValue(leer("fuentes.json"), Fuente[].class));
            if (!fuentes.stream().map(Fuente::id).toList().equals(List.of("terminos-tecsup", "privacidad-tecsup")))
                throw new IllegalStateException("El conjunto de fuentes institucionales está incompleto");
            String terminos = AVISO + "\n\n" + leer("terminos.md") + transcripcion(fuentes.get(0));
            String privacidad = AVISO + "\n\n" + leer("privacidad.md") + transcripcion(fuentes.get(1));
            String hash = sha256((terminos + "\n" + privacidad).getBytes(StandardCharsets.UTF_8));
            return new Documentos(VERSION, "FUENTES_OFICIALES_TECSUP_CON_AVISO_FOUNDIA", terminos, privacidad, hash,
                    AVISO, fuentes);
        } catch (Exception e) { throw new IllegalStateException("No se pudieron cargar los documentos de registro", e); }
    }

    private static String transcripcion(Fuente fuente) throws Exception {
        String original = switch (fuente.id()) {
            case "terminos-tecsup" -> "terminos-tecsup.html.origen";
            case "privacidad-tecsup" -> "privacidad-tecsup.pdf";
            default -> throw new IllegalStateException("Fuente institucional desconocida");
        };
        String texto = leer("fuentes/" + fuente.id() + ".txt");
        if (!sha256(bytes("fuentes/" + original)).equals(fuente.sha256Original())
                || !sha256(texto.getBytes(StandardCharsets.UTF_8)).equals(fuente.sha256Texto()))
            throw new IllegalStateException("La fuente institucional no coincide con su huella");
        var url = java.net.URI.create(fuente.url());
        if (!"https".equals(url.getScheme()) || !"www.tecsup.edu.pe".equals(url.getHost())
                || url.getUserInfo() != null || url.getPort() != -1)
            throw new IllegalStateException("La fuente no pertenece al sitio oficial de Tecsup");
        java.time.LocalDate.parse(fuente.consultadoEl());
        // Metadatos y texto forman parte de la huella que acepta la cuenta.
        return "\nFuente: " + fuente.titulo() + "\nURL: " + fuente.url()
                + "\nConsulta: " + fuente.consultadoEl() + "\nSHA-256 original: " + fuente.sha256Original()
                + "\nSHA-256 transcripción: " + fuente.sha256Texto() + "\n\n" + texto
                + "\nFin de la referencia institucional. La aceptación de FoundIA no autoriza publicidad de Tecsup.\n";
    }

    private static String sha256(byte[] bytes) throws java.security.NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String leer(String nombre) throws java.io.IOException {
        return new String(bytes(nombre), StandardCharsets.UTF_8).replace("\r\n", "\n").replace("\r", "\n");
    }

    private static byte[] bytes(String nombre) throws java.io.IOException {
        try (var entrada = new ClassPathResource("legal/" + VERSION + "/" + nombre).getInputStream()) {
            return entrada.readAllBytes();
        }
    }
}
