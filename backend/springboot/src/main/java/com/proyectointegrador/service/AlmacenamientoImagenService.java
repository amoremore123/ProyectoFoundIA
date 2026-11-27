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

    /** H64: subcarpeta privada de las fotos de perfil dentro del directorio de imágenes. */
    public static final String CARPETA_PERFILES = "perfiles";
    private static final String RUTA_PERFIL = "perfiles/[0-9a-fA-F-]{36}\\.(jpg|png)";
    /** H62: subcarpeta privada de las fotos del chat; la ruta pública /uploads/chat/** está bloqueada. */
    public static final String CARPETA_CHAT = "chat";
    private static final String RUTA_CHAT = "chat/[0-9a-fA-F-]{36}\\.(jpg|png)";
    private static final String RUTA_PUBLICA_ANTERIOR = "/uploads/[0-9a-fA-F-]{36}\\.(jpg|png)";

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
        String nombre = guardarEn(archivo, directorio);
        return new ImagenGuardada(nombre, "/uploads/" + nombre);
    }

    /**
     * H64: guarda la foto de perfil en la subcarpeta privada {@code perfiles/}, con las
     * mismas reglas que las fotos de publicaciones. Devuelve la ruta interna
     * ({@code perfiles/<uuid>.jpg}); la ruta pública {@code /uploads/perfiles/**} está bloqueada.
     */
    public String guardarPerfil(MultipartFile archivo) {
        return CARPETA_PERFILES + "/" + guardarEn(archivo, directorio.resolve(CARPETA_PERFILES));
    }

    /** H64: archivo de una foto de perfil, o vacío si la ruta no es válida o no existe. */
    public java.util.Optional<Path> archivoPerfil(String ruta) {
        if (ruta == null || !ruta.matches(RUTA_PERFIL)) {
            return java.util.Optional.empty();
        }
        Path archivo = directorio.resolve(ruta).normalize();
        return archivo.startsWith(directorio.resolve(CARPETA_PERFILES)) && Files.isRegularFile(archivo)
                ? java.util.Optional.of(archivo) : java.util.Optional.empty();
    }

    /** H62: guarda la foto de un mensaje del chat en la subcarpeta privada; devuelve {@code chat/<uuid>.jpg}. */
    public String guardarChat(MultipartFile archivo) {
        return CARPETA_CHAT + "/" + guardarEn(archivo, directorio.resolve(CARPETA_CHAT));
    }

    /**
     * H62: archivo de una foto del chat. Acepta la ruta privada ({@code chat/...}) y, mientras no se
     * haya movido, la pública anterior ({@code /uploads/...}). Vacío si la ruta no es válida o no existe.
     */
    public java.util.Optional<Path> archivoChat(String ruta) {
        if (ruta == null) return java.util.Optional.empty();
        Path archivo;
        Path base;
        if (ruta.matches(RUTA_CHAT)) {
            base = directorio.resolve(CARPETA_CHAT);
            archivo = directorio.resolve(ruta).normalize();
        } else if (ruta.matches(RUTA_PUBLICA_ANTERIOR)) {
            base = directorio;
            archivo = directorio.resolve(ruta.substring("/uploads/".length())).normalize();
        } else {
            return java.util.Optional.empty();
        }
        return archivo.getParent().equals(base) && Files.isRegularFile(archivo)
                ? java.util.Optional.of(archivo) : java.util.Optional.empty();
    }

    /**
     * H62: mueve a {@code chat/} una foto del chat guardada antes en la carpeta pública y devuelve su
     * ruta nueva. Si el archivo ya no existe, igual devuelve la ruta privada: deja de apuntar a la pública.
     */
    public String moverAChat(String rutaPublica) throws IOException {
        if (rutaPublica == null || !rutaPublica.matches(RUTA_PUBLICA_ANTERIOR)) {
            throw new IllegalArgumentException("No es una ruta pública de foto del chat");
        }
        String nombre = rutaPublica.substring("/uploads/".length());
        Path origen = directorio.resolve(nombre).normalize();
        Path carpeta = directorio.resolve(CARPETA_CHAT);
        Files.createDirectories(carpeta);
        if (Files.isRegularFile(origen)) {
            Files.move(origen, carpeta.resolve(nombre), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return CARPETA_CHAT + "/" + nombre;
    }

    /**
     * H34: borra una foto anotada al eliminar una cuenta: de perfil ({@code perfiles/...}), del chat
     * ({@code chat/...}) o de una publicación ({@code /uploads/<uuid>.jpg}). Cualquier otra ruta se rechaza,
     * para que una fila manipulada no pueda borrar otra cosa del volumen.
     *
     * @return {@code true} si la ruta era válida (exista o no el archivo)
     */
    public boolean borrarFotoAnotada(String ruta) {
        java.util.Optional<Path> archivo;
        if (ruta != null && ruta.matches(RUTA_PERFIL)) {
            archivo = archivoPerfil(ruta);
        } else if (ruta != null && (ruta.matches(RUTA_CHAT) || ruta.matches(RUTA_PUBLICA_ANTERIOR))) {
            archivo = archivoChat(ruta);
        } else {
            return false;
        }
        archivo.ifPresent(a -> {
            try {
                Files.deleteIfExists(a);
            } catch (IOException ignorado) {
                // Se vuelve a intentar en la siguiente vuelta: la fila no se borra si el archivo sigue ahí
            }
        });
        return true;
    }

    /** H64: borra una foto de perfil; no hace nada con rutas que no sean de {@code perfiles/}. */
    public void eliminarPerfil(String ruta) {
        archivoPerfil(ruta).ifPresent(archivo -> {
            try {
                Files.deleteIfExists(archivo);
            } catch (IOException ignored) {
                // La limpieza es compensatoria; un archivo huérfano no afecta a la cuenta.
            }
        });
    }

    private String guardarEn(MultipartFile archivo, Path carpeta) {
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
        Path destino = carpeta.resolve(nombre).normalize();
        if (!destino.getParent().equals(carpeta.normalize())) {
            throw new ImagenInvalidaException("El nombre de archivo no es válido.");
        }

        try {
            Files.createDirectories(carpeta);
            Files.write(destino, contenido, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            return nombre;
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar la imagen.", ex);
        }
    }

    /** Coincidencias por imagen: archivo de la foto de una publicación, o vacío si el nombre no es válido. */
    public java.util.Optional<Path> archivoPublicacion(String nombre) {
        if (nombre == null || !nombre.matches("[0-9a-fA-F-]{36}\\.(jpg|png)")) {
            return java.util.Optional.empty();
        }
        Path archivo = directorio.resolve(nombre).normalize();
        return archivo.getParent().equals(directorio) && Files.isRegularFile(archivo)
                ? java.util.Optional.of(archivo) : java.util.Optional.empty();
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
