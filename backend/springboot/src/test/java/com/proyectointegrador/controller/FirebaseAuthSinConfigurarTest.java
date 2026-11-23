package com.proyectointegrador.controller;

import com.jayway.jsonpath.JsonPath;
import com.proyectointegrador.entity.EstadoUsuario;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.repository.UsuarioRepository;
import com.proyectointegrador.service.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** H72: sin cuenta de servicio la ruta responde 503 y la sesión normal sigue funcionando. */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:firebase_h72_apagado;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off", "logging.level.root=WARN", "app.push.firebase-credenciales-json="})
@AutoConfigureMockMvc
class FirebaseAuthSinConfigurarTest {

    @Autowired MockMvc api;
    @Autowired UsuarioRepository usuarios;
    @MockitoBean EmailService email;

    @Test
    @DisplayName("Sin Firebase: 503 con mensaje claro, y la sesión de FoundIA sigue sirviendo")
    void sinFirebase() throws Exception {
        Usuario u = new Usuario();
        u.setNombre("Ana");
        u.setApellido("QA");
        u.setCorreo("ana-h72-apagado@prueba.invalid");
        u.setPassword(new BCryptPasswordEncoder(4).encode("Segura123!"));
        u.setRol(Rol.USUARIO);
        u.setEstado(EstadoUsuario.ACTIVO);
        u.setVerificado(true);
        usuarios.saveAndFlush(u);
        String jwt = JsonPath.read(api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana-h72-apagado@prueba.invalid\",\"password\":\"Segura123!\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.token");
        api.perform(post("/api/auth/firebase-token").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("Firebase no está configurado")));
        api.perform(get("/api/perfil").header("Authorization", "Bearer " + jwt)).andExpect(status().isOk());
        api.perform(post("/api/auth/logout-all").header("Authorization", "Bearer " + jwt)).andExpect(status().isOk());
    }
}
