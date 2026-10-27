package com.proyectointegrador.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.TipoObjeto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:categorias_s2;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off","logging.level.root=WARN"})
@AutoConfigureMockMvc
class CategoriasPublicacionIntegrationTest extends Sprint2Fixtures {
    @Autowired ObjectMapper json;

    @Test void desactivadaSeConservaEnCatalogoHistoricoPeroNoPermiteNuevasPublicaciones() throws Exception {
        var previo = objeto(ana, TipoObjeto.PERDIDO);
        categoria.setEstado(false); categorias.saveAndFlush(categoria);
        api.perform(get("/api/categorias")).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].estado").value(false));
        api.perform(post("/api/objetos").with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cambio(previo)))).andExpect(status().isBadRequest());
        assertThat(objetos.count()).isEqualTo(1);
        assertThat(objetos.findById(previo.getId()).orElseThrow().getCategoria().getId()).isEqualTo(categoria.getId());
    }

    @Test void permiteEditarElAntecedenteSinRomperSuCategoriaDesactivada() throws Exception {
        var previo = objeto(ana, TipoObjeto.PERDIDO);
        categoria.setEstado(false); categorias.saveAndFlush(categoria);
        api.perform(put("/api/objetos/"+previo.getId()).with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cambio(previo)))).andExpect(status().isOk());
        Categoria otra = new Categoria(); otra.setNombre("Otra desactivada"); otra.setEstado(false);
        otra = categorias.saveAndFlush(otra);
        var cuerpo = json.readTree(json.writeValueAsString(cambio(previo)));
        ((com.fasterxml.jackson.databind.node.ObjectNode)cuerpo).put("categoriaId", otra.getId());
        api.perform(put("/api/objetos/"+previo.getId()).with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(cuerpo))).andExpect(status().isBadRequest());
        assertThat(objetos.findById(previo.getId()).orElseThrow().getCategoria().getId()).isEqualTo(categoria.getId());
    }
}
