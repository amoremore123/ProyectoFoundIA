package com.proyectointegrador.controller;

import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.ReconocimientoService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Reconocimiento a quien devuelve: solo cuenta lo verificado por la biblioteca")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_reconocimiento_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class ReconocimientoIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MockMvc api;

    private Usuario ana;
    private Usuario luis;
    private Categoria categoria;

    @BeforeEach
    void preparar() {
        // La tabla de custodias la maneja el panel (Django); en H2 se crea antes de todo (el DDL confirma la transacción)
        jdbc.execute("""
                create table if not exists custodias (id bigint auto_increment primary key, objeto_id bigint,
                  codigo varchar(20), token_qr varchar(500), recibido_por_id bigint,
                  fecha_recepcion timestamp, observaciones text, estado varchar(20))""");
        ana = usuario("ana-reconocimiento@prueba.invalid", "Ana");
        luis = usuario("luis-reconocimiento@prueba.invalid", "Luis");
        categoria = new Categoria();
        categoria.setNombre("Reconocimiento");
        categoria.setEstado(true);
        entityManager.persist(categoria);
        entityManager.flush();
    }

    private Usuario usuario(String correo, String nombre) {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setApellido("Prueba");
        u.setCorreo(correo);
        u.setPassword("no-se-utiliza");
        u.setVerificado(true);
        entityManager.persist(u);
        return u;
    }

    private Long objeto(Usuario autor, TipoObjeto tipo) {
        Objeto o = new Objeto();
        o.setUsuario(autor);
        o.setCategoria(categoria);
        o.setNombre("Objeto");
        o.setDescripcion("Prueba");
        o.setUbicacion("Biblioteca");
        o.setFechaObjeto(LocalDate.now().minusDays(1));
        o.setTipo(tipo);
        o.setEstado(EstadoObjeto.EN_CUSTODIA);
        entityManager.persist(o);
        entityManager.flush();
        return o.getId();
    }

    private void custodia(Long objetoId, String estado) {
        jdbc.update("insert into custodias (objeto_id, codigo, token_qr, recibido_por_id, fecha_recepcion, estado) "
                + "values (?, ?, 't', 1, current_timestamp, ?)", objetoId, "FND-" + objetoId, estado);
    }

    private RequestPostProcessor como(Usuario usuario) {
        UserPrincipal principal = new UserPrincipal(usuario.getId(), usuario.getNombre(), usuario.getCorreo(),
                usuario.getPassword(), com.proyectointegrador.entity.Rol.USUARIO);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    void cuentaSoloLosObjetosQueEncontroYEntregoEnLaBiblioteca() throws Exception {
        custodia(objeto(ana, TipoObjeto.ENCONTRADO), "RETIRADO");
        custodia(objeto(ana, TipoObjeto.ENCONTRADO), "EN_CUSTODIA");
        custodia(objeto(ana, TipoObjeto.ENCONTRADO), "RETIRADO");
        objeto(ana, TipoObjeto.ENCONTRADO);                    // publicado pero nunca entregado: no cuenta
        custodia(objeto(luis, TipoObjeto.ENCONTRADO), "RETIRADO"); // de otra persona: no cuenta

        api.perform(get("/api/perfil/reconocimiento").with(como(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entregados").value(3))
                .andExpect(jsonPath("$.devueltos").value(2))
                .andExpect(jsonPath("$.nivel.nombre").value("Buen compañero"))
                .andExpect(jsonPath("$.siguiente.nombre").value("Guardián de Tecsup"))
                .andExpect(jsonPath("$.siguiente.faltan").value(2));
    }

    @Test
    void sinEntregasNoHayInsigniaYSinSesionNoResponde() throws Exception {
        api.perform(get("/api/perfil/reconocimiento").with(como(luis)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entregados").value(0))
                .andExpect(jsonPath("$.nivel").doesNotExist())
                .andExpect(jsonPath("$.siguiente.faltan").value(1));
        api.perform(get("/api/perfil/reconocimiento")).andExpect(status().isUnauthorized());
    }

    @Test
    void nivelesPorCantidad() {
        assertThat(ReconocimientoService.calcular(0, 0).nivel()).isNull();
        assertThat(ReconocimientoService.calcular(1, 0).nivel().nombre()).isEqualTo("Primera entrega");
        assertThat(ReconocimientoService.calcular(10, 4).nivel().nombre()).isEqualTo("Leyenda FoundIA");
        assertThat(ReconocimientoService.calcular(12, 4).siguiente()).isNull();
    }
}
