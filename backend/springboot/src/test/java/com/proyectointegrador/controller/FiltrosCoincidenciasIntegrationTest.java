package com.proyectointegrador.controller;

import com.proyectointegrador.entity.*;
import com.proyectointegrador.repository.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:filtros_coincidencias;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect","logging.level.root=WARN"})
@AutoConfigureMockMvc
@Transactional
class FiltrosCoincidenciasIntegrationTest {
    @Autowired MockMvc api;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired ObjetoRepository objetos;
    @Autowired CoincidenciaRepository coincidencias;
    @Autowired EntityManager datos;
    Usuario ana,luis;
    Categoria categoria;
    Objeto cerca,medio,lejos,sinGps,base,similar,parcial;
    LocalDate hoy=LocalDate.now();
    @BeforeEach void preparar() {
        ana=usuario("Ana"); luis=usuario("Luis");
        categoria=categoria("Mochilas");
        cerca=objeto("Mochila cerca",TipoObjeto.ENCONTRADO,luis,"Biblioteca 100%",hoy,0d,.001);
        medio=objeto("Mochila media",TipoObjeto.PERDIDO,ana,"Biblioteca",hoy.minusDays(1),0d,.01);
        lejos=objeto("Mochila lejos",TipoObjeto.ENCONTRADO,luis,"Comedor",hoy.minusDays(2),0d,.1);
        sinGps=objeto("Sin GPS",TipoObjeto.PERDIDO,ana,"Biblioteca",hoy,null,null);
        var oculta=objeto("Oculta",TipoObjeto.ENCONTRADO,luis,"Biblioteca",hoy,0d,0d);oculta.setEstado(EstadoObjeto.OCULTO);
        var eliminada=objeto("Eliminada",TipoObjeto.ENCONTRADO,luis,"Biblioteca",hoy,0d,0d);eliminada.setEstado(EstadoObjeto.ELIMINADO);
        categoria=categoria("Llaves");
        base=objeto("Llaves azules",TipoObjeto.PERDIDO,ana,"Cómputo",hoy,10d,10d);
        similar=objeto("LLAVES azúles",TipoObjeto.ENCONTRADO,luis,"Computo",hoy,10d,10d);
        parcial=objeto("Llaves rojas",TipoObjeto.ENCONTRADO,luis,"Cómputo",hoy,null,null);
        objeto("Llaves azules",TipoObjeto.ENCONTRADO,ana,"Cómputo",hoy,null,null);
        objetos.flush();
    }
    Usuario usuario(String nombre) {
        var u=new Usuario();u.setNombre(nombre);u.setApellido("Privado");u.setCorreo(nombre+"@qa.invalid");u.setPassword("secreto");u.setVerificado(true);return usuarios.save(u);
    }
    Categoria categoria(String nombre) {var c=new Categoria();c.setNombre(nombre);return categorias.save(c);}
    Objeto objeto(String nombre,TipoObjeto tipo,Usuario autor,String lugar,LocalDate fecha,Double lat,Double lon) {
        var o=new Objeto();o.setNombre(nombre);o.setDescripcion("Texto de prueba");o.setCategoria(categoria);o.setUsuario(autor);
        o.setTipo(tipo);o.setUbicacion(lugar);o.setFechaObjeto(fecha);o.setLatitud(lat==null?null:BigDecimal.valueOf(lat));o.setLongitud(lon==null?null:BigDecimal.valueOf(lon));return objetos.save(o);
    }
    String par() {return base.getId()+"-"+similar.getId();}

    @Test void proximidadSeOrdenaPorDistanciaYOmiteSinGPSYOcultos() throws Exception {
        api.perform(get("/api/objetos/ubicacion").param("latitud","0").param("longitud","0").param("radioKm","2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(cerca.getId())).andExpect(jsonPath("$[1].id").value(medio.getId()));
    }
    @Test void radioPorDefectoCincoKmYRadioMayor() throws Exception {
        api.perform(get("/api/objetos/ubicacion?latitud=0&longitud=0")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        api.perform(get("/api/objetos/ubicacion?latitud=0&longitud=0&radioKm=12")).andExpect(jsonPath("$[2].id").value(lejos.getId()));
    }
    @Test void ubicacionEsTextoLiteralYNoComodinSQL() throws Exception {
        api.perform(get("/api/objetos/ubicacion").param("ubicacion","100%"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].id").value(cerca.getId()));
    }
    @Test void sinResultadosCercanosDevuelveVacio() throws Exception {
        api.perform(get("/api/objetos/ubicacion?latitud=-80&longitud=-170&radioKm=1")).andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"latitud=0","longitud=0","latitud=91&longitud=0","latitud=0&longitud=181","latitud=NaN&longitud=0","latitud=0&longitud=Infinity","radioKm=2","latitud=0&longitud=0&radioKm=0","latitud=0&longitud=0&radioKm=-1","latitud=0&longitud=0&radioKm=1001","latitud=0&longitud=0&radioKm=NaN","latitud=texto&longitud=0"})
    void coordenadasYRadioInvalidosSon400(String query) throws Exception {
        api.perform(get("/api/objetos/ubicacion?"+query)).andExpect(status().isBadRequest());
    }
    @Test void limiteDeRadioIncluyeSoloLasDistanciasDentroDelRadio() throws Exception {
        double distancia = 6371d * Math.toRadians(.001);
        api.perform(get("/api/objetos/ubicacion").param("latitud", "0").param("longitud", "0")
                .param("radioKm", Double.toString(distancia + .000001)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        api.perform(get("/api/objetos/ubicacion").param("latitud", "0").param("longitud", "0")
                .param("radioKm", Double.toString(distancia - .000001)))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());
    }
}
