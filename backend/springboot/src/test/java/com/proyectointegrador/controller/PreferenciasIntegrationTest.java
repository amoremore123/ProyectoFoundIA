package com.proyectointegrador.controller;
import com.proyectointegrador.dto.PreferenciasRequest;
import com.proyectointegrador.entity.*;
import com.proyectointegrador.repository.*;
import com.proyectointegrador.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import com.proyectointegrador.dto.ContactoRequest;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:preferencias_s2;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off","logging.level.root=WARN"})
@AutoConfigureMockMvc
class PreferenciasIntegrationTest extends Sprint2Fixtures {
    @Autowired PreferenciaNotificacionRepository preferencias;
    @Autowired PreferenciaNotificacionService servicio;
    @Autowired AvisoCoincidenciaService avisar;
    @Autowired AvisoCoincidenciaRepository avisos;
    @Autowired NotificacionService notificar;
    @Autowired ContactoService contactos;
    static final String RUTA="/api/notificaciones/preferencias";
    static final String TODOS_FALSE="{\"coincidencias\":false,\"contactos\":false,\"reclamos\":false}";

    @Test void valoresPorDefectoNoEscribenYSonSoloPropios() throws Exception {
        api.perform(get(RUTA)).andExpect(status().isUnauthorized());
        api.perform(get(RUTA).with(como(ana))).andExpect(status().isOk()).andExpect(jsonPath("$.coincidencias").value(true));
        assertThat(preferencias.count()).isZero();
        api.perform(put(RUTA).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content(TODOS_FALSE)).andExpect(status().isOk());
        api.perform(get(RUTA).with(como(ana))).andExpect(jsonPath("$.coincidencias").value(false));
        api.perform(get(RUTA).with(como(luis))).andExpect(jsonPath("$.coincidencias").value(true));
        assertThat(preferencias.findAll()).hasSize(1); assertThat(preferencias.findAll().get(0).getUsuarioId()).isEqualTo(ana.getId());
    }

    @Test void requiereTodosLosBooleanosSinAceptarRutaDeOtraCuenta() throws Exception {
        api.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content(TODOS_FALSE)).andExpect(status().isUnauthorized());
        for(String body:new String[]{"{}","{\"coincidencias\":null}","{\"coincidencias\":[]}"})
            api.perform(put(RUTA).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        api.perform(put(RUTA+"/"+luis.getId()).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content(TODOS_FALSE)).andExpect(status().isNotFound());
        assertThat(preferencias.count()).isZero();
    }

    @Test void preferenciaSoloAfectaParejasNuevasNoReactivaLasOmitidas() {
        servicio.guardar(ana.getId(),new PreferenciasRequest(false,true,true));
        Objeto perdido=objeto(ana,TipoObjeto.PERDIDO), hallazgo=objeto(luis,TipoObjeto.ENCONTRADO);
        emitirEvento(hallazgo.getId());
        assertThat(avisos.count()).isEqualTo(1); assertThat(notificaciones.count()).isZero();
        servicio.guardar(ana.getId(),new PreferenciasRequest(true,true,true));
        tx.executeWithoutResult(t -> avisar.alGuardar(new ObjetoGuardado(hallazgo.getId())));
        assertThat(notificaciones.count()).isZero();
        Objeto otro=objeto(luis,TipoObjeto.ENCONTRADO);
        emitirEvento(otro.getId());
        assertThat(notificaciones.count()).isEqualTo(1); assertThat(avisos.count()).isEqualTo(2);
    }

    @Test void noBorraHistorialYNuevosContactosReclamosRespetanPreferencias() {
        notificar.notificarReclamo(ana,1L);
        servicio.guardar(ana.getId(),new PreferenciasRequest(false,false,false));
        notificar.notificarReclamo(ana,2L); notificar.notificarSolicitudContacto(ana,2L);
        assertThat(notificaciones.count()).isEqualTo(1);
        assertThat(servicio.permite(ana.getId(),"CONTACTO_RECIBIDO")).isFalse();
        assertThat(servicio.permite(ana.getId(),"RECLAMO_RECIBIDO")).isFalse();
    }

    @RepeatedTest(10)
    void primerCambioDePreferenciaYContactoRespetanElMismoOrdenDeBloqueos() throws Exception {
        var perdido=objeto(ana,TipoObjeto.PERDIDO);var hallazgo=objeto(luis,TipoObjeto.ENCONTRADO);
        var pool=Executors.newFixedThreadPool(2);var salida=new CountDownLatch(1);
        try {
            var a=pool.submit(() -> {salida.await(5,TimeUnit.SECONDS);return contactos.iniciar(new ContactoRequest(perdido.getId(),hallazgo.getId()),ana.getId());});
            var b=pool.submit(() -> {salida.await(5,TimeUnit.SECONDS);return servicio.guardar(luis.getId(),new PreferenciasRequest(true,false,true));});
            salida.countDown();a.get(30,TimeUnit.SECONDS);b.get(30,TimeUnit.SECONDS);
            assertThat(preferencias.findById(luis.getId()).orElseThrow().isContactos()).isFalse();
            assertThat(notificaciones.count()).isBetween(0L,1L);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM contactos",Long.class)).isEqualTo(1L);
        } finally {pool.shutdownNow();}
    }
}
