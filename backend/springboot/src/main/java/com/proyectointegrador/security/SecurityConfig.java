package com.proyectointegrador.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final CorsConfigurationSource corsConfigurationSource;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          JsonAuthenticationEntryPoint authenticationEntryPoint,
                          CorsConfigurationSource corsConfigurationSource) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.corsConfigurationSource = corsConfigurationSource;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // H37: el cierre de sesión exige un token válido.
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout", "/api/auth/logout-all", "/api/auth/firebase-token").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/legal/documentos").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categorias").permitAll()
                        // Swagger: solo existe si SWAGGER_HABILITADO=true; apagado, estas rutas responden 404
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html",
                                "/swagger-ui/**").permitAll()
                        // H71: avisos destacados vigentes, públicos
                        .requestMatchers(HttpMethod.GET, "/api/avisos-destacados").permitAll()
                        // H67: visita que llegó por el enlace de una campaña (sin datos personales)
                        .requestMatchers(HttpMethod.POST, "/api/campanas/visitas").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/objetos", "/api/objetos/**").permitAll()
                        // H64: las fotos de perfil son privadas; se ven solo con sesión en /api/perfil/foto
                        .requestMatchers("/uploads/perfiles/**").denyAll()
                        // H62: las fotos del chat se piden con sesión en /api/conversaciones/{id}/mensajes/{m}/foto
                        .requestMatchers("/uploads/chat/**").denyAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/coincidencias/**").permitAll()
                        // H41: al escanear la etiqueta de custodia se ve el objeto (sin datos personales)
                        .requestMatchers(HttpMethod.GET, "/api/custodias/verificar").permitAll()
                        // H45: interpretación pública, con resumen propio cuando hay sesión.
                        .requestMatchers(HttpMethod.POST, "/api/asistente/interpretar").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/asistente/voz").permitAll()
                        // Buscar con una foto en el chat de FINDI (la foto no se guarda)
                        .requestMatchers(HttpMethod.POST, "/api/asistente/buscar-por-foto").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/objetos", "/api/objetos/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/objetos", "/api/objetos/**").authenticated()
                        .requestMatchers(HttpMethod.DELETE, "/api/objetos", "/api/objetos/**").authenticated()
                        .requestMatchers("/api/perfil", "/api/perfil/**").authenticated()
                        .requestMatchers("/api/notificaciones", "/api/notificaciones/**").authenticated()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(authenticationEntryPoint))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
