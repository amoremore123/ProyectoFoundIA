package com.proyectointegrador.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI de la API de Spring (la del panel, Django, va aparte). Solo se publica con
 * SWAGGER_HABILITADO=true. El botón «Authorize» acepta el token de POST /api/auth/login.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI foundiaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FoundIA — API de usuarios (Spring Boot)")
                        .version("Sprint 4")
                        .description("Objetos perdidos y encontrados de Tecsup. Inicia sesión con POST /api/auth/login "
                                + "y pega el token en «Authorize». Los contratos detallados están en docs/api/."))
                .components(new Components().addSecuritySchemes("jwt", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("jwt"));
    }
}
