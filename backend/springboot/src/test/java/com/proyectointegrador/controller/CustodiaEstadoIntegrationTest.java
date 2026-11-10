package com.proyectointegrador.controller;

import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.entity.Usuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("H41 - el estado EN_CUSTODIA se conserva en detalle y búsquedas")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_custodia_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class CustodiaEstadoIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc api;

    private Long objetoId;

    @BeforeEach
    void prepararObjetoEnCustodia() {
        Usuario autor = new Usuario();
        autor.setNombre("Ana");
        autor.setApellido("Prueba");
        autor.setCorreo("h41-custodia@prueba.invalid");
        autor.setPassword("no-se-utiliza");
        autor.setVerificado(true);
        entityManager.persist(autor);

        Categoria categoria = new Categoria();
        categoria.setNombre("Custodia H41");
        categoria.setEstado(true);
        entityManager.persist(categoria);

        Objeto objeto = new Objeto();
        objeto.setUsuario(autor);
        objeto.setCategoria(categoria);
        objeto.setNombre("Mochila azul");
        objeto.setDescripcion("Objeto entregado en biblioteca");
        objeto.setUbicacion("Biblioteca central");
        objeto.setFechaObjeto(LocalDate.now().minusDays(1));
        objeto.setTipo(TipoObjeto.ENCONTRADO);
        objeto.setEstado(EstadoObjeto.EN_CUSTODIA);
        entityManager.persist(objeto);
        entityManager.flush();
        objetoId = objeto.getId();
    }

    @Test
    @DisplayName("El detalle público informa que el objeto está en custodia")
    void detalleMuestraEstadoEnCustodia() throws Exception {
        api.perform(get("/api/objetos/{id}", objetoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(objetoId))
                .andExpect(jsonPath("$.estado").value("EN_CUSTODIA"));
    }

    @Test
    @DisplayName("La búsqueda y el listado siguen mostrando el objeto en custodia")
    void busquedaYListadoIncluyenCustodia() throws Exception {
        api.perform(get("/api/objetos/buscar").param("q", "Mochila"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(objetoId))
                .andExpect(jsonPath("$[0].estado").value("EN_CUSTODIA"));

        api.perform(get("/api/objetos").param("estado", "EN_CUSTODIA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(objetoId))
                .andExpect(jsonPath("$[0].estado").value("EN_CUSTODIA"));
    }
}
