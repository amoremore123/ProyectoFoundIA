package com.proyectointegrador.controller;

import com.proyectointegrador.dto.ReporteRequest;
import com.proyectointegrador.entity.*;
import com.proyectointegrador.repository.ReporteRepository;
import com.proyectointegrador.service.ReporteService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:reportes_s2;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off","logging.level.root=WARN"})
@AutoConfigureMockMvc
class ReportarPublicacionIntegrationTest extends Sprint2Fixtures {
    @Autowired ReporteRepository reportes;
    @Autowired ReporteService servicio;
    Objeto publicacion;

    @BeforeEach void publicar() { publicacion = objeto(luis, TipoObjeto.ENCONTRADO); }
    String ruta() { return "/api/objetos/"+publicacion.getId()+"/reportes"; }

    @Test void registraAlEmisorRealSinExponerContactosNiModerarLaPublicacion() throws Exception {
        String cuerpo = api.perform(post(ruta()).with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"SPAM\",\"descripcion\":\"  Repetido  \",\"usuarioId\":"+luis.getId()+"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.repetido").value(false)).andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain(ana.getCorreo(), luis.getCorreo(), "password", "Reservado");
        var fila = reportes.findAll().get(0);
        assertThat(fila.getUsuario().getId()).isEqualTo(ana.getId());
        assertThat(fila.getDescripcion()).isEqualTo("Repetido");
        assertThat(objetos.findById(publicacion.getId()).orElseThrow().getEstado()).isEqualTo(EstadoObjeto.ACTIVO);
    }

    @Test void repetirConservaMotivoFechaYUnaSolaDenuncia() throws Exception {
        var uno = servicio.reportar(publicacion.getId(), ana.getId(), new ReporteRequest("SPAM", "Primero"));
        api.perform(post(ruta()).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"INFORMACION_FALSA\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(uno.id()))
                .andExpect(jsonPath("$.motivo").value("SPAM")).andExpect(jsonPath("$.repetido").value(true));
        assertThat(reportes.count()).isEqualTo(1);
        assertThat(reportes.findAll().get(0).getDescripcion()).isEqualTo("Primero");
    }

    @Test void requiereSesionYCuentaHabilitada() throws Exception {
        api.perform(post(ruta()).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
        ana.setEstado(EstadoUsuario.SUSPENDIDO); usuarios.saveAndFlush(ana);
        api.perform(post(ruta()).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"SPAM\"}"))
                .andExpect(status().isUnauthorized());
        assertThat(reportes.count()).isZero();
    }

    @ParameterizedTest @ValueSource(strings={"{}","null","{\"motivo\":\"\"}","{\"motivo\":\"inventado\"}","{\"motivo\":\"OTRO\",\"descripcion\":\"   \"}"})
    void rechazaDatosInvalidosSinGuardar(String cuerpo) throws Exception {
        api.perform(post(ruta()).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isBadRequest());
        assertThat(reportes.count()).isZero();
    }

    @Test void noReportaPublicacionesRetiradas() throws Exception {
        for (var estado : new EstadoObjeto[]{EstadoObjeto.OCULTO, EstadoObjeto.ELIMINADO}) {
            publicacion.setEstado(estado); objetos.saveAndFlush(publicacion);
            api.perform(post(ruta()).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"SPAM\"}"))
                    .andExpect(status().isNotFound());
        }
        assertThat(reportes.count()).isZero();
    }

    @Test void dosEnviosConcurrentesNoDuplican() throws Exception {
        var pool = Executors.newFixedThreadPool(2); var salida = new CountDownLatch(1);
        try {
            var a=pool.submit(() -> { salida.await(5,TimeUnit.SECONDS); return servicio.reportar(publicacion.getId(),ana.getId(),new ReporteRequest("SPAM",null)); });
            var b=pool.submit(() -> { salida.await(5,TimeUnit.SECONDS); return servicio.reportar(publicacion.getId(),ana.getId(),new ReporteRequest("SPAM",null)); });
            salida.countDown(); var uno=a.get(30,TimeUnit.SECONDS); var dos=b.get(30,TimeUnit.SECONDS);
            assertThat(uno.id()).isEqualTo(dos.id()); assertThat(uno.repetido()).isNotEqualTo(dos.repetido());
            assertThat(reportes.count()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
}
