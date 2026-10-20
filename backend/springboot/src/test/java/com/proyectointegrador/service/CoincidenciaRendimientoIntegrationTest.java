package com.proyectointegrador.service;

import com.proyectointegrador.entity.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:coincidencias_carga;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
    "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.generate_statistics=true",
    "logging.level.root=WARN", "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"
})
@Transactional
class CoincidenciaRendimientoIntegrationTest {
    @Autowired EntityManager datos;
    @Autowired EntityManagerFactory fabrica;
    @Autowired CoincidenciaService motor;

    @Test
    void quinientosCandidatosSinConsultasPorCadaFila() {
        var ana = usuario("ana"); var luis = usuario("luis");
        var categoria = new Categoria(); categoria.setNombre("QA carga"); datos.persist(categoria);
        var base = objeto(ana, categoria, TipoObjeto.PERDIDO);
        for (int i = 0; i < 500; i++) {
            objeto(luis, categoria, TipoObjeto.ENCONTRADO);
            objeto(luis, categoria, TipoObjeto.PERDIDO);
        }
        datos.flush(); datos.clear();
        var estadisticas = fabrica.unwrap(SessionFactory.class).getStatistics();
        var mediciones = new ArrayList<Long>();
        for (int i = 0; i < 5; i++) {
            datos.clear(); estadisticas.clear();
            long inicio = System.nanoTime();
            var resultado = motor.listar(base.getId());
            long milisegundos = (System.nanoTime() - inicio) / 1_000_000;
            mediciones.add(milisegundos);
            assertThat(resultado).hasSize(500);
            assertThat(resultado.get(0).porcentaje()).isEqualByComparingTo("100");
            assertThat(estadisticas.getPrepareStatementCount()).isLessThanOrEqualTo(8);
            assertThat(milisegundos).as("Presupuesto QA local, no SLA de producción").isLessThan(5000);
        }
        System.out.println("H16_RENDIMIENTO catalogo=1001 candidatos=500 muestras_ms=" + mediciones
                + " consultas_ultima=" + estadisticas.getPrepareStatementCount());
    }

    private Usuario usuario(String nombre) {
        var u = new Usuario(); u.setNombre(nombre); u.setApellido("QA");
        u.setCorreo(nombre + "@carga.qa.invalid"); u.setPassword("no-utilizado"); u.setVerificado(true);
        datos.persist(u); return u;
    }

    private Objeto objeto(Usuario autor, Categoria categoria, TipoObjeto tipo) {
        var o = new Objeto(); o.setUsuario(autor); o.setCategoria(categoria); o.setTipo(tipo);
        o.setNombre("Mochila azul"); o.setDescripcion("Datos sintéticos de carga");
        o.setUbicacion("Biblioteca"); o.setFechaObjeto(LocalDate.now());
        datos.persist(o); return o;
    }
}
