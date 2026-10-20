package com.proyectointegrador.service;

import com.proyectointegrador.exception.ImagenInvalidaException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;

@Service
public class AlmacenamientoImagenService {

    private final Path directorio;
    private final long maximoBytes;
    private final long maximoPixeles;

    public AlmacenamientoImagenService(
            @Value("${app.upload.directory:uploads}") String directorio,
            @Value("${app.upload.max-size-bytes:5242880}") long maximoBytes,
            @Value("${app.upload.max-pixels:40000000}") long maximoPixeles) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
        this.maximoBytes = maximoBytes;
        this.maximoPixeles = maximoPixeles;
    }

    public ImagenGuardada guardar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ImagenInvalidaException("Selecciona una imagen JPEG o PNG.");
        }
        if (archivo.getSize() > maximoBytes) {
            throw new ImagenInvalidaException("La imagen supera el máximo de 5 MiB.");
        }

        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException ex) {
            throw new ImagenInvalidaException("No se pudo leer la imagen enviada.");
        }
        String extension = validarImagen(contenido);
        String nombre = UUID.randomUUID() + extension;
        Path destino = directorio.resolve(nombre).normalize();
        if (!destino.getParent().equals(directorio)) {
            throw new ImagenInvalidaException("El nombre de archivo no es válido.");
        }

        try {
            Files.createDirectories(directorio);
            Files.write(destino, contenido, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return new ImagenGuardada(nombre, "/uploads/" + nombre);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar la imagen.", ex);
        }
    }

    public void eliminar(String nombre) {
        if (nombre == null || !nombre.matches("[0-9a-fA-F-]{36}\\.(jpg|png)")) {
            return;
        }
        try {
            Files.deleteIfExists(directorio.resolve(nombre).normalize());
        } catch (IOException ignored) {
            // La limpieza es compensatoria; se conserva el error original del registro.
        }
    }

    private String validarImagen(byte[] contenido) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            if (entrada == null) {
                throw new ImagenInvalidaException("El archivo no es una imagen válida.");
            }
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                throw new ImagenInvalidaException("Formato no admitido: usa JPEG o PNG.");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, true, true);
                String formato = lector.getFormatName().toLowerCase(Locale.ROOT);
                String extension = switch (formato) {
                    case "jpeg", "jpg" -> ".jpg";
                    case "png" -> ".png";
                    default -> throw new ImagenInvalidaException("Formato no admitido: usa JPEG o PNG.");
                };
                int ancho = lector.getWidth(0);
                int alto = lector.getHeight(0);
                if (ancho < 1 || alto < 1 || (long) ancho * alto > maximoPixeles) {
                    throw new ImagenInvalidaException("La resolución de la imagen no puede superar 40 megapíxeles.");
                }
                return extension;
            } finally {
                lector.dispose();
            }
        } catch (ImagenInvalidaException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new ImagenInvalidaException("El archivo no es una imagen JPEG o PNG válida.");
        }
    }

    public record ImagenGuardada(String nombre, String url) {
    }
}
