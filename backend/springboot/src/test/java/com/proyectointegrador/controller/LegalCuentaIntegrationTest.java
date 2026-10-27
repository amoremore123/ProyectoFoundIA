package com.proyectointegrador.controller;

import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.repository.*;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:legal_cuenta;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
 "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
 "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
 "spring.main.banner-mode=off","logging.level.root=WARN"})
@AutoConfigureMockMvc
class LegalCuentaIntegrationTest {
    @Autowired MockMvc api;
    @Autowired UsuarioRepository usuarios;
    @Autowired SolicitudEliminacionRepository solicitudes;
    @Autowired EliminacionCuentaService servicio;
    @MockitoBean EmailService correo;
    static final String RUTA="/api/perfil/solicitud-eliminacion";
    @BeforeEach void limpiar() { solicitudes.deleteAll(); usuarios.deleteAll(); reset(correo); }
    @AfterEach void cerrar() { solicitudes.deleteAll(); usuarios.deleteAll(); }
    String registro(String extra) { return "{\"nombre\":\"Amore\",\"apellido\":\"Prueba\",\"correo\":\"nueva@qa.invalid\",\"password\":\"Segura123!\""+extra+"}"; }
    @Test void documentosPublicosVersionadosSinSuplantarInstitucion() throws Exception {
        api.perform(get("/api/legal/documentos")).andExpect(status().isOk())
          .andExpect(jsonPath("$.version").value(DocumentosLegales.VERSION))
          .andExpect(jsonPath("$.sha256").value(org.hamcrest.Matchers.matchesPattern("[a-f0-9]{64}")))
          .andExpect(jsonPath("$.estado").value("TEXTO_DEL_PROYECTO_PENDIENTE_REVISION_INSTITUCIONAL"));
    }
    @Test void sinAceptarNoRegistraNiEnviaCorreo() throws Exception {
        for (String extra : List.of("", ",\"aceptaTerminos\":false,\"versionLegal\":\""+DocumentosLegales.VERSION+"\"",
              ",\"aceptaTerminos\":true,\"versionLegal\":\"vieja\""))
            api.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registro(extra)))
                .andExpect(status().isBadRequest());
        assertThat(usuarios.count()).isZero(); verifyNoInteractions(correo);
    }
    @Test void registraConsentimientoVerificableSinInventarElDeCuentasAnteriores() throws Exception {
        var anterior = usuario(); assertThat(anterior.getAceptacionLegalFecha()).isNull();
        api.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
           .content(registro(",\"aceptaTerminos\":true,\"versionLegal\":\""+DocumentosLegales.VERSION+"\"")))
           .andExpect(status().isCreated());
        var nuevo = usuarios.findByCorreo("nueva@qa.invalid").orElseThrow();
        assertThat(nuevo.getVersionLegal()).isEqualTo(DocumentosLegales.VERSION);
        assertThat(nuevo.getDocumentosLegalesHash()).isEqualTo(DocumentosLegales.documentos().sha256());
        assertThat(nuevo.getAceptacionLegalFecha()).isNotNull(); assertThat(nuevo.isVerificado()).isFalse();
        assertThat(usuarios.findById(anterior.getId()).orElseThrow().getVersionLegal()).isNull();
    }
    @Test void correoFallidoNoDejaAceptacionNiCuenta() throws Exception {
        doThrow(new com.proyectointegrador.exception.EnvioCorreoException("Fallo de prueba"))
             .when(correo).enviarCodigoVerificacion(anyString(),anyString(),anyString(),anyLong());
        api.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
           .content(registro(",\"aceptaTerminos\":true,\"versionLegal\":\""+DocumentosLegales.VERSION+"\"")))
           .andExpect(status().isServiceUnavailable());
        assertThat(usuarios.count()).isZero();
    }
    Usuario usuario() {
        var u=new Usuario();u.setNombre("Prueba");u.setApellido("QA");u.setCorreo(UUID.randomUUID()+"@qa.invalid");
        u.setPassword("hash-previo");u.setVerificado(true);return usuarios.saveAndFlush(u);
    }
    org.springframework.test.web.servlet.request.RequestPostProcessor como(Usuario u) {
        var p=UserPrincipal.from(u);return authentication(new UsernamePasswordAuthenticationToken(p,null,p.getAuthorities()));
    }
    @Test void requiereSesionYConfirmacionSinCrearAlConsultarOCancelar() throws Exception {
        api.perform(get(RUTA)).andExpect(status().isUnauthorized());
        api.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"confirmar\":true}"))
            .andExpect(status().isUnauthorized());
        var u=usuario();
        api.perform(get(RUTA).with(como(u))).andExpect(jsonPath("$.estado").value("SIN_SOLICITUD"));
        for(String body:List.of("{}","{\"confirmar\":false}","{\"confirmar\":true,\"usuarioId\":999}"))
          api.perform(post(RUTA).with(como(u)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        assertThat(solicitudes.count()).isZero();
    }
    @Test void repeticionConservaFechaYDatosSoloDelTitular() throws Exception {
        var u=usuario();var otra=usuario();
        api.perform(post(RUTA).with(como(u)).contentType(MediaType.APPLICATION_JSON).content("{\"confirmar\":true}"))
          .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE"));
        var antes=solicitudes.findByUsuarioId(u.getId()).orElseThrow();
        api.perform(post(RUTA).with(como(u)).contentType(MediaType.APPLICATION_JSON).content("{\"confirmar\":true}"))
          .andExpect(status().isOk()).andExpect(jsonPath("$.repetido").value(true));
        api.perform(get(RUTA).with(como(otra))).andExpect(jsonPath("$.estado").value("SIN_SOLICITUD"));
        assertThat(solicitudes.count()).isEqualTo(1);
        assertThat(solicitudes.findByUsuarioId(u.getId()).orElseThrow().getFechaSolicitud()).isEqualTo(antes.getFechaSolicitud());
        assertThat(usuarios.findById(u.getId()).orElseThrow().getPassword()).isEqualTo("hash-previo");
        assertThat(usuarios.count()).isEqualTo(2);
    }
    @Test void dosSolicitudesSimultaneasSonUnSoloTramite() throws Exception {
        var u=usuario();var pool=Executors.newFixedThreadPool(2);var inicio=new CountDownLatch(1);
        try {
            Callable<EliminacionCuentaService.Estado> tarea=()->{inicio.await();return servicio.solicitar(u.getId());};
            var a=pool.submit(tarea);var b=pool.submit(tarea);inicio.countDown();
            var r1=a.get(10,TimeUnit.SECONDS);var r2=b.get(10,TimeUnit.SECONDS);
            assertThat(r1.id()).isEqualTo(r2.id());assertThat(r1.repetido()).isNotEqualTo(r2.repetido());
            assertThat(solicitudes.count()).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }
}
