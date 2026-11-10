package com.proyectointegrador.controller;

import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Notificacion;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.RecordatorioObjeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.repository.PreferenciaNotificacionRepository;
import com.proyectointegrador.repository.RecordatorioObjetoRepository;
import com.proyectointegrador.service.RecordatorioObjetoGenerador;
import com.proyectointegrador.service.RecordatorioObjetoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recordatorios_h53;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off",
        "logging.level.root=WARN",
        "app.recordatorios.habilitados=false"})
@AutoConfigureMockMvc
class RecordatoriosIntegrationTest extends Sprint2Fixtures {

    @Autowired RecordatorioObjetoService servicio;
    @Autowired RecordatorioObjetoRepository registros;
    @Autowired PreferenciaNotificacionRepository preferenciasRepo;

    static final String RUTA = "/api/notificaciones/preferencias";

    private Objeto objetoPublicadoHace(Usuario usuario, TipoObjeto tipo, long dias, EstadoObjeto estado) {
        Objeto objeto = new Objeto();
        objeto.setUsuario(usuario);
        objeto.setCategoria(categoria);
        objeto.setNombre("Objeto " + tipo + " de " + dias + " días");
        objeto.setDescripcion("Descripción QA");
        objeto.setUbicacion("Biblioteca");
        objeto.setFechaObjeto(LocalDate.now().minusDays(dias + 1));
        objeto.setTipo(tipo);
        objeto.setEstado(estado);
        objeto.setFechaPublicacion(LocalDateTime.now(ZoneOffset.UTC)
                .minusDays(dias).truncatedTo(ChronoUnit.SECONDS));
        return objetos.saveAndFlush(objeto);
    }

    @Test
    void avisaSoloPerdidosActivosAntiguosSinRecuperacion() {
        Objeto antiguo = objetoPublicadoHace(ana, TipoObjeto.PERDIDO, 8, EstadoObjeto.ACTIVO);
        objetoPublicadoHace(ana, TipoObjeto.PERDIDO, 2, EstadoObjeto.ACTIVO);
        Objeto recuperado = objetoPublicadoHace(luis, TipoObjeto.PERDIDO, 8, EstadoObjeto.RECUPERADO);
        recuperado.setFechaRecuperacion(LocalDateTime.now(ZoneOffset.UTC));
        objetos.saveAndFlush(recuperado);
        Objeto conFechaRecuperacion = objetoPublicadoHace(luis, TipoObjeto.PERDIDO, 8, EstadoObjeto.ACTIVO);
        conFechaRecuperacion.setFechaRecuperacion(LocalDateTime.now(ZoneOffset.UTC));
        objetos.saveAndFlush(conFechaRecuperacion);
        objetoPublicadoHace(luis, TipoObjeto.ENCONTRADO, 8, EstadoObjeto.ACTIVO);
        objetoPublicadoHace(luis, TipoObjeto.PERDIDO, 8, EstadoObjeto.OCULTO);

        int creados = servicio.ejecutar();

        assertThat(creados).isEqualTo(1);
        assertThat(registros.count()).isEqualTo(1);
        assertThat(notificaciones.count()).isEqualTo(1);

        Notificacion aviso = notificaciones.findAll().get(0);
        assertThat(aviso.getUsuario().getId()).isEqualTo(ana.getId());
        assertThat(aviso.getTipo()).isEqualTo(RecordatorioObjetoGenerador.TIPO_AVISO);
        assertThat(aviso.getEnlace()).isEqualTo("/objeto/" + antiguo.getId());
        assertThat(aviso.getTitulo()).contains("sin resolver");
        assertThat(aviso.getMensaje()).contains(antiguo.getNombre());
        assertThat(aviso.getMensaje()).doesNotContain(ana.getCorreo(), luis.getCorreo());
        assertThat(aviso.getLeida()).isFalse();
    }

    @Test
    void noRepiteEnElMismoPeriodoYVuelveEnElSiguiente() {
        objetoPublicadoHace(ana, TipoObjeto.PERDIDO, 8, EstadoObjeto.ACTIVO);

        assertThat(servicio.ejecutar()).isEqualTo(1);
        assertThat(servicio.ejecutar()).isZero();
        assertThat(registros.count()).isEqualTo(1);
        assertThat(notificaciones.count()).isEqualTo(1);

        // Simula que el registro quedó en un periodo anterior.
        RecordatorioObjeto registro = registros.findAll().get(0);
        registro.setPeriodo("2000-01");
        registros.saveAndFlush(registro);

        assertThat(servicio.ejecutar()).isEqualTo(1);
        assertThat(registros.count()).isEqualTo(2);
        assertThat(notificaciones.count()).isEqualTo(2);
    }

    @Test
    void respetaLaPreferenciaYConservaElCampoSiElClienteH24NoLoSEnvia() throws Exception {
        api.perform(get(RUTA).with(como(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordatorios").value(true));
        assertThat(preferenciasRepo.count()).isZero();

        api.perform(put(RUTA).with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coincidencias\":true,\"contactos\":true,\"reclamos\":true,\"recordatorios\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordatorios").value(false));

        // Un cliente anterior a H53 envía solo los tres campos: no debe reactivarlos.
        api.perform(put(RUTA).with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coincidencias\":true,\"contactos\":true,\"reclamos\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordatorios").value(false));

        objetoPublicadoHace(ana, TipoObjeto.PERDIDO, 8, EstadoObjeto.ACTIVO);
        assertThat(servicio.ejecutar()).isZero();
        assertThat(notificaciones.count()).isZero();
        assertThat(registros.count()).isZero();

        api.perform(put(RUTA).with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coincidencias\":true,\"contactos\":true,\"reclamos\":true,\"recordatorios\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordatorios").value(true));

        assertThat(servicio.ejecutar()).isEqualTo(1);
        assertThat(notificaciones.count()).isEqualTo(1);
        assertThat(registros.count()).isEqualTo(1);
    }
}
