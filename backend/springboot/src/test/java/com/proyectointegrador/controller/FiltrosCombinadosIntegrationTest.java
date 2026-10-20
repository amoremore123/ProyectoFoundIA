package com.proyectointegrador.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.entity.Usuario;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:filtros_parejas;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "logging.level.root=WARN"
})
@AutoConfigureMockMvc
@Transactional
class FiltrosCombinadosIntegrationTest {

    private static final List<String> FILTROS = List.of("texto", "categoria", "tipo", "ubicacion", "fecha", "proximidad");
    private static final LocalDate FECHA = LocalDate.of(2026, 10, 1);

    @Autowired private EntityManager datos;
    @Autowired private MockMvc api;
    @Autowired private ObjectMapper json;

    private Objeto completo;
    private final List<Objeto> controles = new ArrayList<>();

    @BeforeEach
    void preparar() {
        var autor = new Usuario();
        autor.setNombre("Ana");
        autor.setApellido("QA");
        autor.setCorreo("parejas@qa.invalid");
        autor.setPassword("no-utilizado");
        datos.persist(autor);

        var categoria = new Categoria();
        categoria.setNombre("Mochilas QA");
        datos.persist(categoria);
        var otraCategoria = new Categoria();
        otraCategoria.setNombre("Otras QA");
        datos.persist(otraCategoria);

        completo = objeto(autor, categoria, -1);
        // Cada control incumple exactamente un filtro; un OR incluiría controles indebidos.
        for (int i = 0; i < FILTROS.size(); i++) {
            controles.add(objeto(autor, i == 1 ? otraCategoria : categoria, i));
        }
        datos.flush();
        datos.clear();
    }

    static Stream<Arguments> combinaciones() {
        Stream<Arguments> parejas = IntStream.range(0, FILTROS.size()).boxed()
                .flatMap(i -> IntStream.range(i + 1, FILTROS.size())
                        .mapToObj(j -> Arguments.of(List.of(FILTROS.get(i), FILTROS.get(j)))));
        return Stream.concat(parejas, Stream.of(Arguments.of(FILTROS), Arguments.of(List.of())));
    }

    @ParameterizedTest(name = "AND en BD: {0}")
    @MethodSource("combinaciones")
    void parejasTodosYLimpiarRespetanCadaFiltro(List<String> activos) throws Exception {
        var consulta = get("/api/objetos/buscar");
        for (String filtro : activos) {
            switch (filtro) {
                case "texto" -> consulta.param("q", "Mochila");
                case "categoria" -> consulta.param("categoriaId", completo.getCategoria().getId().toString());
                case "tipo" -> consulta.param("tipo", "ENCONTRADO");
                case "ubicacion" -> consulta.param("ubicacion", "Biblioteca");
                case "fecha" -> consulta.param("desde", FECHA.toString()).param("hasta", FECHA.toString());
                case "proximidad" -> consulta.param("latitud", "0").param("longitud", "0").param("radioKm", "1");
                default -> throw new AssertionError("Filtro desconocido");
            }
        }

        var esperados = new ArrayList<Long>();
        esperados.add(completo.getId());
        for (int i = 0; i < controles.size(); i++) {
            if (!activos.contains(FILTROS.get(i))) esperados.add(controles.get(i).getId());
        }
        String respuesta = api.perform(consulta).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var obtenidos = new ArrayList<Long>();
        for (JsonNode objeto : json.readTree(respuesta)) obtenidos.add(objeto.get("id").longValue());
        assertThat(obtenidos).containsExactlyInAnyOrderElementsOf(esperados);
    }

    private Objeto objeto(Usuario autor, Categoria categoria, int diferente) {
        var objeto = new Objeto();
        objeto.setUsuario(autor);
        objeto.setCategoria(categoria);
        objeto.setNombre(diferente == 0 ? "Cartera" : "Mochila");
        objeto.setDescripcion("Datos sintéticos de filtros");
        objeto.setTipo(diferente == 2 ? TipoObjeto.PERDIDO : TipoObjeto.ENCONTRADO);
        objeto.setUbicacion(diferente == 3 ? "Comedor" : "Biblioteca");
        objeto.setFechaObjeto(diferente == 4 ? FECHA.minusDays(1) : FECHA);
        objeto.setLatitud(BigDecimal.ZERO);
        objeto.setLongitud(diferente == 5 ? BigDecimal.ONE : BigDecimal.ZERO);
        datos.persist(objeto);
        return objeto;
    }
}
