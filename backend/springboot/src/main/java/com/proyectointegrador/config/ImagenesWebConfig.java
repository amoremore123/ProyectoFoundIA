package com.proyectointegrador.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class ImagenesWebConfig implements WebMvcConfigurer {

    private final Path directorio;

    public ImagenesWebConfig(@Value("${app.upload.directory:uploads}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String ubicacion = directorio.toUri().toString();
        if (!ubicacion.endsWith("/")) {
            ubicacion += "/";
        }
        // Primero las fotos subidas (volumen); si no está, las fotos de los datos de ejemplo (seed.sql),
        // que vienen dentro de la aplicación para que funcionen en cualquier base sin copiar archivos.
        registry.addResourceHandler("/uploads/**").addResourceLocations(ubicacion, "classpath:/semilla/uploads/");
    }
}
