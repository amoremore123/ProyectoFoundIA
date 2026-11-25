package com.proyectointegrador.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documentación de la API en vivo: http://localhost:8081/swagger-ui.html (contrato en /v3/api-docs).
 * El botón «Authorize» recibe el token JWT de /api/auth/login para probar las rutas con sesión.
 */
@Configuration
public class OpenApiConfig {

    static final String ESQUEMA_JWT = "jwt";

    @Bean
    public OpenAPI foundiaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FoundIA · API de la comunidad (Spring Boot)")
                        .version("Sprint 4")
                        .description("""
                                Objetos perdidos y encontrados de Tecsup, sede Santa Anita: cuentas, publicaciones, \
                                coincidencias, reclamos, chat, etiquetas QR y FINDI (asistente con IA). La biblioteca \
                                (custodia, respaldos, campañas) está en la API de Django. Los errores responden con \
                                ApiError: {timestamp, status, error, mensaje, path}.""")
                        .contact(new Contact().name("Equipo FoundIA · Tecsup")))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Token de POST /api/auth/login (campo «token»)")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}
