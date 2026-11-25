package com.proyectointegrador.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Caché en memoria (Caffeine). Hoy guarda la lista de categorías, que pide cada pantalla de la web y
 * de la app. Vence a los 60 s (spring.cache.caffeine.spec) porque las categorías las edita el panel
 * de Django y Spring no se entera de esos cambios.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CATEGORIAS = "categorias";
}
