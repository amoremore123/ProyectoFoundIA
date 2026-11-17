package com.proyectointegrador.controller;

import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.service.CustodiaPublicaService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("H41 - al escanear la etiqueta de custodia se ve el objeto, sin datos personales")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_custodia_publica_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off",
        "logging.level.root=WARN"
})
@AutoConfigureMockMvc
@Transactional
class CustodiaPublicaIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc api;
    @Value("${app.jwt.secret}") private String secreto;

    private Long objetoId;
    private String token;

    @BeforeEach
    void preparar() {
        // La tabla la maneja el panel (Django); en H2 se crea solo para la prueba,
        // antes de todo, porque el DDL confirma la transacción en curso
        jdbc.execute("""
                create table if not exists custodias (id bigint auto_increment primary key, objeto_id bigint,
                  codigo varchar(20), token_qr varchar(500), recibido_por_id bigint,
                  fecha_recepcion timestamp, observaciones text, estado varchar(20))""");
        Usuario autor = new Usuario();
        autor.setNombre("Ana");
        autor.setApellido("Secreta");
        autor.setCorreo("h41-publica@prueba.invalid");
        autor.setPassword("no-se-utiliza");
        autor.setVerificado(true);
        entityManager.persist(autor);
        Categoria categoria = new Categoria();
        categoria.setNombre("Mochilas");
        categoria.setEstado(true);
        entityManager.persist(categoria);
        Objeto objeto = new Objeto();
        objeto.setUsuario(autor);
        objeto.setCategoria(categoria);
        objeto.setNombre("Mochila azul");
        objeto.setDescripcion("Con un llavero");
        objeto.setUbicacion("Biblioteca central");
        objeto.setFechaObjeto(LocalDate.now().minusDays(1));
        objeto.setTipo(TipoObjeto.ENCONTRADO);
        objeto.setEstado(EstadoObjeto.EN_CUSTODIA);
        entityManager.persist(objeto);
        entityManager.flush();
        objetoId = objeto.getId();

        token = firmar("FND-2026-0099", objetoId, "custodia");
        jdbc.update("insert into custodias (objeto_id, codigo, token_qr, recibido_por_id, fecha_recepcion, estado) "
                + "values (?, 'FND-2026-0099', ?, 1, current_timestamp, 'EN_CUSTODIA')", objetoId, token);
    }

    private String firmar(String codigo, Long objeto, String tipo) {
        return Jwts.builder().setSubject(codigo).claim("objeto", objeto).claim("tipo", tipo)
                .setIssuedAt(new Date())
                .signWith(Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }

    @Test
    void sinSesionMuestraElObjetoYNoLosDatosDeQuienLoDejo() throws Exception {
        api.perform(get("/api/custodias/verificar").param("t", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("FND-2026-0099"))
                .andExpect(jsonPath("$.estado").value("EN_CUSTODIA"))
                .andExpect(jsonPath("$.objeto.id").value(objetoId))
                .andExpect(jsonPath("$.objeto.nombre").value("Mochila azul"))
                .andExpect(jsonPath("$.objeto.categoria").value("Mochilas"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Secreta"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("prueba.invalid"))));
    }

    @Test
    void rechazaTokensManipuladosDeOtroTipoOReemplazados() throws Exception {
        api.perform(get("/api/custodias/verificar").param("t", token.substring(0, token.length() - 4) + "AAAA"))
                .andExpect(status().isNotFound());
        api.perform(get("/api/custodias/verificar").param("t", firmar("FND-2026-0099", objetoId, "sesion")))
                .andExpect(status().isNotFound());
        // Firmado y válido, pero no es el token vigente de la etiqueta (se volvió a emitir)
        String anterior = Jwts.builder().setSubject("FND-2026-0099").claim("objeto", objetoId).claim("tipo", "custodia")
                .setIssuedAt(new Date(1_000_000L))
                .signWith(Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
        api.perform(get("/api/custodias/verificar").param("t", anterior))
                .andExpect(status().isNotFound());
        api.perform(get("/api/custodias/verificar")).andExpect(status().isNotFound());
    }

    @Test
    void elEnlaceCompletoDelQrTambienSirve() {
        String enlace = "http://192.168.1.5:5173/custodia/verificar?t=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
        assertThat(CustodiaPublicaService.extraerToken(enlace)).isEqualTo(token);
        assertThat(CustodiaPublicaService.extraerToken("  " + token + " ")).isEqualTo(token);
        assertThat(CustodiaPublicaService.extraerToken("https://otra.web/sin-token")).isEmpty();
    }
}
