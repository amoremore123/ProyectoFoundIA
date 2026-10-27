package com.proyectointegrador.controller;

import com.proyectointegrador.entity.*;
import com.proyectointegrador.repository.AvisoCoincidenciaRepository;
import com.proyectointegrador.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:avisos_s2;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off", "logging.level.root=WARN"})
@AutoConfigureMockMvc
class AvisosCoincidenciaIntegrationTest extends Sprint2Fixtures {
    @Autowired AvisoCoincidenciaService avisar;
    @Autowired AvisoCoincidenciaRepository avisos;
    @Autowired ObjetoService publicaciones;
    @Autowired GestionCoincidenciaService gestion;

    @Test void publicarYEditarHallazgoAvisaUnaVezAlTitularCorrecto() throws Exception {
        Objeto perdido = objeto(ana, TipoObjeto.PERDIDO);
        java.time.Instant antes=java.time.Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        var request = """
                {"categoriaId":%d,"nombre":"Mochila azul","descripcion":"Descripción QA","ubicacion":"Biblioteca",
                 "fechaObjeto":"%s","tipo":"ENCONTRADO"}
                """.formatted(categoria.getId(), perdido.getFechaObjeto());
        api.perform(post("/api/objetos").with(como(luis)).contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isCreated());
        Objeto hallazgo = objetos.findAll().stream().filter(o -> o.getTipo()==TipoObjeto.ENCONTRADO).findFirst().orElseThrow();
        publicaciones.actualizar(hallazgo.getId(), cambio(hallazgo), luis.getId());
        esperarAvisos();
        assertThat(avisos.count()).isEqualTo(1); assertThat(notificaciones.count()).isEqualTo(1);
        var aviso = notificaciones.findAll().get(0);
        assertThat(hallazgo.getFechaPublicacion().toInstant(java.time.ZoneOffset.UTC)).isBetween(antes,java.time.Instant.now());
        assertThat(aviso.getFechaCreacion().toInstant(java.time.ZoneOffset.UTC)).isBetween(antes,java.time.Instant.now());
        assertThat(aviso.getUsuario().getId()).isEqualTo(ana.getId());
        assertThat(aviso.getEnlace()).isEqualTo("/coincidencias/"+perdido.getId()+"-"+hallazgo.getId());
        api.perform(get("/api/notificaciones").with(como(ana))).andExpect(status().isOk()).andExpect(jsonPath("$[0].enlace").value(aviso.getEnlace()));
        api.perform(get("/api/notificaciones").with(como(luis))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        api.perform(get("/api/coincidencias/"+perdido.getId()+"-"+hallazgo.getId())).andExpect(status().isOk());
        assertThat(aviso.getMensaje()).doesNotContain(ana.getCorreo(), luis.getCorreo(), "Reservado");
    }

    @Test void losGetNoCreanAvisosYUnDescarteNoReaparece() throws Exception {
        Objeto perdido = objeto(ana, TipoObjeto.PERDIDO), encontrado = objeto(luis, TipoObjeto.ENCONTRADO);
        for (int n=0;n<2;n++) api.perform(get("/api/objetos/"+perdido.getId()+"/coincidencias")).andExpect(status().isOk());
        assertThat(avisos.count()).isZero(); assertThat(notificaciones.count()).isZero();
        gestion.descartar(perdido.getId()+"-"+encontrado.getId(), ana.getId());
        emitirEvento(encontrado.getId());
        assertThat(avisos.count()).isZero();
    }

    @Test void publicarPerdidoSobreHallazgoExistenteTambienAvisa() {
        objeto(luis, TipoObjeto.ENCONTRADO); Objeto perdido=objeto(ana, TipoObjeto.PERDIDO);
        emitirEvento(perdido.getId());
        assertThat(notificaciones.findAll()).hasSize(1).allSatisfy(n -> assertThat(n.getUsuario().getId()).isEqualTo(ana.getId()));
    }

    @Test void dosEventosConcurrentesNoDuplicanLaPareja() throws Exception {
        objeto(ana, TipoObjeto.PERDIDO); Objeto hallazgo=objeto(luis, TipoObjeto.ENCONTRADO);
        var pool=Executors.newFixedThreadPool(2); var salida=new CountDownLatch(1);
        try {
            Callable<Boolean> tarea=() -> { salida.await(5,TimeUnit.SECONDS); emitirEvento(hallazgo.getId()); return true; };
            var a=pool.submit(tarea); var b=pool.submit(tarea); salida.countDown(); a.get(30,TimeUnit.SECONDS); b.get(30,TimeUnit.SECONDS);
            assertThat(avisos.count()).isEqualTo(1); assertThat(notificaciones.count()).isEqualTo(1);
        } finally {pool.shutdownNow();}
    }

    @Test void noAvisaCoincidenciasPropiasRecuperadasNiDeBajaSimilitud() {
        Objeto perdido=objeto(ana,TipoObjeto.PERDIDO); objeto(ana,TipoObjeto.ENCONTRADO);
        Objeto ajeno=objeto(luis,TipoObjeto.ENCONTRADO); ajeno.setEstado(EstadoObjeto.RECUPERADO); objetos.saveAndFlush(ajeno);
        Objeto otro=objeto(luis,TipoObjeto.ENCONTRADO); otro.setNombre("Llaves rojas"); otro.setUbicacion("Otro lugar");
        otro.setFechaObjeto(perdido.getFechaObjeto().minusDays(50)); objetos.saveAndFlush(otro);
        emitirEvento(perdido.getId());
        assertThat(notificaciones.count()).isZero();
    }

    @Test void falloDeAvisoConservaPublicacionYEventoParaReintentarSinDuplicar() throws Exception {
        objeto(ana,TipoObjeto.PERDIDO);
        jdbc.execute("ALTER TABLE notificaciones ADD CONSTRAINT qa_h23_aviso CHECK (tipo <> 'COINCIDENCIA_NUEVA')");
        try {
            api.perform(post("/api/objetos").with(como(luis)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"categoriaId\":"+categoria.getId()+",\"nombre\":\"Mochila azul\",\"descripcion\":\"QA\",\"ubicacion\":\"Biblioteca\",\"fechaObjeto\":\""+java.time.LocalDate.now().minusDays(1)+"\",\"tipo\":\"ENCONTRADO\"}"))
                    .andExpect(status().isCreated());
            esperarAvisos();
            assertThat(objetos.count()).isEqualTo(2);assertThat(notificaciones.count()).isZero();assertThat(avisos.count()).isZero();
            assertThat(eventos.findAll()).hasSize(1).allSatisfy(e -> assertThat(e.getEstado()).isEqualTo("PENDIENTE"));
        } finally {
            try(var c=jdbc.getDataSource().getConnection()) {
                jdbc.execute("ALTER TABLE notificaciones DROP "+(c.getMetaData().getDatabaseProductName().equals("MySQL")?"CHECK":"CONSTRAINT")+" qa_h23_aviso");
            }
        }
        avisar.reintentar();avisar.reintentar();
        assertThat(notificaciones.count()).isEqualTo(1);assertThat(avisos.count()).isEqualTo(1);
        assertThat(eventos.findAll()).allSatisfy(e -> {assertThat(e.getEstado()).isEqualTo("COMPLETADO");assertThat(e.getIntentos()).isGreaterThanOrEqualTo(2);});
    }
}
