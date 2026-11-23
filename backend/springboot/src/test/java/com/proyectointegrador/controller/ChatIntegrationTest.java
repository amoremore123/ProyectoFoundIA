package com.proyectointegrador.controller;

import com.jayway.jsonpath.JsonPath;
import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.Coincidencia;
import com.proyectointegrador.entity.Contacto;
import com.proyectointegrador.entity.EstadoCoincidencia;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.NivelCoincidencia;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.Reclamo;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("H55 a H63 - chat entre personas y con la biblioteca")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_chat_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class ChatIntegrationTest {

    @Autowired
    private EntityManager em;

    @Autowired
    private MockMvc mvc;

    private Usuario perdedor;
    private Usuario hallador;
    private Usuario tercero;
    private Usuario bibliotecaria;
    private Categoria categoria;
    private Objeto perdido;
    private Objeto encontrado;
    private Contacto contacto;

    @BeforeEach
    void preparar() {
        perdedor = usuario("perdedor-chat@prueba.invalid", "Ana", Rol.USUARIO);
        hallador = usuario("hallador-chat@prueba.invalid", "Beto", Rol.USUARIO);
        tercero = usuario("tercero-chat@prueba.invalid", "Cris", Rol.USUARIO);
        bibliotecaria = usuario("biblio-chat@prueba.invalid", "Lupe", Rol.BIBLIOTECA);
        categoria = new Categoria();
        categoria.setNombre("Categoría chat " + UUID.randomUUID());
        categoria.setEstado(true);
        em.persist(categoria);
        perdido = objeto(perdedor, TipoObjeto.PERDIDO, "Mochila perdida");
        encontrado = objeto(hallador, TipoObjeto.ENCONTRADO, "Mochila hallada");
        Coincidencia coincidencia = new Coincidencia();
        coincidencia.setObjetoPerdido(perdido);
        coincidencia.setObjetoEncontrado(encontrado);
        coincidencia.setPorcentaje(BigDecimal.valueOf(80));
        coincidencia.setNivel(NivelCoincidencia.ALTA);
        coincidencia.setEstado(EstadoCoincidencia.PENDIENTE);
        em.persist(coincidencia);
        contacto = new Contacto();
        contacto.setCoincidencia(coincidencia);
        contacto.setUsuarioEmisor(perdedor);
        contacto.setUsuarioReceptor(hallador);
        contacto.setMensaje("Solicitud de contacto");
        em.persist(contacto);
        em.flush();
    }

    private Usuario usuario(String correo, String nombre, Rol rol) {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setApellido("Prueba");
        u.setCorreo(correo);
        u.setPassword("no-se-utiliza");
        u.setVerificado(true);
        u.setRol(rol);
        em.persist(u);
        return u;
    }

    private Objeto objeto(Usuario dueno, TipoObjeto tipo, String nombre) {
        Objeto o = new Objeto();
        o.setUsuario(dueno);
        o.setCategoria(categoria);
        o.setNombre(nombre);
        o.setDescripcion("Descripción de prueba");
        o.setUbicacion("Biblioteca");
        o.setFechaObjeto(LocalDate.now().minusDays(1));
        o.setTipo(tipo);
        o.setEstado(EstadoObjeto.ACTIVO);
        em.persist(o);
        return o;
    }

    private RequestPostProcessor como(Usuario u) {
        UserPrincipal p = new UserPrincipal(u.getId(), u.getNombre(), u.getCorreo(), u.getPassword(), u.getRol());
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private long iniciarEntrePersonas() throws Exception {
        String cuerpo = mvc.perform(post("/api/conversaciones").with(como(perdedor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactoId\":" + contacto.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("ENTRE_USUARIOS"))
                .andExpect(jsonPath("$.otro.nombre").value("Beto"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private void escribir(Usuario autor, long id, String texto) throws Exception {
        mvc.perform(post("/api/conversaciones/{id}/mensajes", id).with(como(autor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"" + texto + "\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Ambos participantes conversan, ven sin leer y marcan como leída")
    void conversacionEntrePersonas() throws Exception {
        long id = iniciarEntrePersonas();
        // Iniciarla otra vez, incluso por el otro lado, devuelve la misma conversación.
        mvc.perform(post("/api/conversaciones").with(como(hallador)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactoId\":" + contacto.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));

        escribir(perdedor, id, "Hola, creo que es mi mochila");
        escribir(hallador, id, "Dime de qué color es");

        mvc.perform(get("/api/conversaciones").with(como(perdedor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sinLeer").value(1))
                .andExpect(jsonPath("$[0].ultimoMensaje.texto").value("Dime de qué color es"));
        mvc.perform(get("/api/conversaciones/{id}/mensajes", id).with(como(perdedor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensajes.length()").value(2))
                .andExpect(jsonPath("$.mensajes[0].autorNombre").value("Ana"));
        mvc.perform(post("/api/conversaciones/{id}/leida", id).with(como(perdedor)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/conversaciones/{id}", id).with(como(perdedor)))
                .andExpect(jsonPath("$.sinLeer").value(0));
        // El cursor devuelve solo lo posterior.
        mvc.perform(get("/api/conversaciones/{id}/mensajes?despuesDe=1000", id).with(como(perdedor)))
                .andExpect(jsonPath("$.mensajes.length()").value(0));

        // El otro participante recibió un aviso sin el texto del mensaje.
        mvc.perform(get("/api/notificaciones").with(como(hallador)))
                .andExpect(jsonPath("$[?(@.tipo=='MENSAJE_CHAT')]").isNotEmpty());
    }

    @Test
    @DisplayName("Una tercera persona no ve ni escribe en la conversación")
    void terceroNoAccede() throws Exception {
        long id = iniciarEntrePersonas();
        mvc.perform(get("/api/conversaciones/{id}/mensajes", id).with(como(tercero))).andExpect(status().isNotFound());
        mvc.perform(post("/api/conversaciones/{id}/mensajes", id).with(como(tercero))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"intruso\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/conversaciones").with(como(tercero)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactoId\":" + contacto.getId() + "}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/conversaciones").with(como(tercero)))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/conversaciones")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un mensaje vacío o demasiado largo se rechaza")
    void validaMensaje() throws Exception {
        long id = iniciarEntrePersonas();
        mvc.perform(post("/api/conversaciones/{id}/mensajes", id).with(como(perdedor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/conversaciones/{id}/mensajes", id).with(como(perdedor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"texto\":\"" + "a".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Bloquear impide escribir a ambos y desbloquear lo restablece")
    void bloqueo() throws Exception {
        long id = iniciarEntrePersonas();
        mvc.perform(post("/api/conversaciones/{id}/bloqueo", id).with(como(hallador)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bloqueada").value(true));
        mvc.perform(post("/api/conversaciones/{id}/mensajes", id).with(como(perdedor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"hola\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/conversaciones/{id}/mensajes", id).with(como(hallador))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"hola\"}"))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/conversaciones/{id}/bloqueo", id).with(como(hallador)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.bloqueada").value(false));
        escribir(perdedor, id, "ya sí");
    }

    @Test
    @DisplayName("Se puede reportar un mensaje ajeno una sola vez y no el propio")
    void reporte() throws Exception {
        long id = iniciarEntrePersonas();
        escribir(hallador, id, "mensaje ofensivo");
        String cuerpo = mvc.perform(get("/api/conversaciones/{id}/mensajes", id).with(como(perdedor)))
                .andReturn().getResponse().getContentAsString();
        long mensajeId = ((Number) JsonPath.read(cuerpo, "$.mensajes[0].id")).longValue();
        String motivo = "{\"motivo\":\"Lenguaje ofensivo\"}";
        mvc.perform(post("/api/conversaciones/{id}/mensajes/{m}/reporte", id, mensajeId).with(como(perdedor))
                        .contentType(MediaType.APPLICATION_JSON).content(motivo))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/conversaciones/{id}/mensajes/{m}/reporte", id, mensajeId).with(como(perdedor))
                        .contentType(MediaType.APPLICATION_JSON).content(motivo))
                .andExpect(status().isNoContent());
        em.flush();
        org.assertj.core.api.Assertions.assertThat(
                em.createQuery("select count(r) from ReporteMensaje r", Long.class).getSingleResult()).isEqualTo(1L);
        mvc.perform(post("/api/conversaciones/{id}/mensajes/{m}/reporte", id, mensajeId).with(como(hallador))
                        .contentType(MediaType.APPLICATION_JSON).content(motivo))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Cuando el objeto se recupera la conversación queda en solo lectura")
    void soloLectura() throws Exception {
        long id = iniciarEntrePersonas();
        escribir(perdedor, id, "antes de cerrar");
        encontrado.setEstado(EstadoObjeto.RECUPERADO);
        em.flush();
        mvc.perform(get("/api/conversaciones/{id}/mensajes", id).with(como(perdedor)))
                .andExpect(jsonPath("$.soloLectura").value(true))
                .andExpect(jsonPath("$.mensajes.length()").value(1));
        mvc.perform(post("/api/conversaciones/{id}/mensajes", id).with(como(perdedor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"tarde\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Al confirmar la recuperación (H22) el objeto perdido pasa a RECUPERADO y la conversación queda en solo lectura")
    void soloLecturaAlRecuperarElObjetoPerdido() throws Exception {
        long id = iniciarEntrePersonas();
        escribir(hallador, id, "¿es tuya?");
        // Confirmar la recuperación cambia el objeto perdido; el encontrado sigue ACTIVO
        perdido.setEstado(EstadoObjeto.RECUPERADO);
        em.flush();
        mvc.perform(get("/api/conversaciones/{id}/mensajes", id).with(como(hallador)))
                .andExpect(jsonPath("$.soloLectura").value(true))
                .andExpect(jsonPath("$.mensajes.length()").value(1));
        for (Usuario quien : new Usuario[]{perdedor, hallador}) {
            mvc.perform(post("/api/conversaciones/{id}/mensajes", id).with(como(quien))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"tarde\"}"))
                    .andExpect(status().isConflict());
        }
    }

    @Test
    @DisplayName("Quien reclama conversa con la biblioteca y el personal responde desde su bandeja")
    void conBiblioteca() throws Exception {
        Objeto custodia = objeto(bibliotecaria, TipoObjeto.ENCONTRADO, "Cartera en custodia");
        custodia.setEstado(EstadoObjeto.EN_CUSTODIA);
        Reclamo reclamo = new Reclamo();
        reclamo.setObjeto(custodia);
        reclamo.setUsuario(tercero);
        em.persist(reclamo);
        em.flush();

        String cuerpo = mvc.perform(post("/api/conversaciones").with(como(tercero))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reclamoId\":" + reclamo.getId() + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("CON_BIBLIOTECA"))
                .andExpect(jsonPath("$.otro.nombre").value("Biblioteca"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(cuerpo, "$.id")).longValue();

        // Otra persona no puede abrir la conversación de ese reclamo.
        mvc.perform(post("/api/conversaciones").with(como(perdedor)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reclamoId\":" + reclamo.getId() + "}"))
                .andExpect(status().isForbidden());

        escribir(tercero, id, "¿Cuándo puedo pasar a recogerla?");
        mvc.perform(get("/api/conversaciones").with(como(bibliotecaria)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sinLeer").value(1));
        escribir(bibliotecaria, id, "Hoy hasta las 6 pm");
        mvc.perform(get("/api/notificaciones").with(como(tercero)))
                .andExpect(jsonPath("$[?(@.tipo=='MENSAJE_CHAT')]").isNotEmpty());
        mvc.perform(get("/api/conversaciones").with(como(perdedor)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Indicar contacto y reclamo a la vez, o ninguno, es una solicitud inválida")
    void iniciarInvalido() throws Exception {
        mvc.perform(post("/api/conversaciones").with(como(perdedor)).contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/conversaciones").with(como(perdedor)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contactoId\":999999}"))
                .andExpect(status().isNotFound());
    }

    // ---------------- H65: Idempotency-Key ----------------

    private org.springframework.test.web.servlet.ResultActions enviarCon(Usuario autor, long id, String texto, String clave)
            throws Exception {
        var pedido = post("/api/conversaciones/{id}/mensajes", id).with(como(autor))
                .contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"" + texto + "\"}");
        return mvc.perform(clave == null ? pedido : pedido.header("Idempotency-Key", clave));
    }

    private long contar(String jpql, Object... parametros) {
        var q = em.createQuery(jpql, Long.class);
        for (int i = 0; i < parametros.length; i++) q.setParameter(i + 1, parametros[i]);
        return q.getSingleResult();
    }

    @Test
    @DisplayName("H65: reenviar con la misma Idempotency-Key devuelve el mismo mensaje y no vuelve a avisar")
    void idempotenciaMensaje() throws Exception {
        long id = iniciarEntrePersonas();
        String clave = "3f2a9c1e-5b7d-4e2a-9c1e-5b7d4e2a9c1e";
        String primero = enviarCon(perdedor, id, "Hola", clave).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number idMensaje = JsonPath.read(primero, "$.id");
        enviarCon(perdedor, id, "Hola", clave).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(idMensaje.longValue()));
        // Aunque el reintento traiga otro texto, la clave identifica el envío
        enviarCon(perdedor, id, "Hola otra vez", clave).andExpect(status().isOk())
                .andExpect(jsonPath("$.texto").value("Hola"));
        em.flush();
        assertThat(contar("SELECT COUNT(m) FROM MensajeChat m WHERE m.conversacion.id = ?1", id)).isEqualTo(1);
        assertThat(contar("SELECT COUNT(n) FROM Notificacion n WHERE n.usuario.id = ?1 AND n.tipo = 'MENSAJE_CHAT'",
                hallador.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("H65: sin clave se comporta como antes y la clave es por cuenta")
    void idempotenciaSinClaveYPorCuenta() throws Exception {
        long id = iniciarEntrePersonas();
        enviarCon(perdedor, id, "Igual", null).andExpect(status().isCreated());
        enviarCon(perdedor, id, "Igual", null).andExpect(status().isCreated());
        // La misma clave de otra persona es otro envío
        enviarCon(perdedor, id, "De Ana", "clave-compartida-123").andExpect(status().isCreated());
        enviarCon(hallador, id, "De Beto", "clave-compartida-123").andExpect(status().isCreated());
        em.flush();
        assertThat(contar("SELECT COUNT(m) FROM MensajeChat m WHERE m.conversacion.id = ?1", id)).isEqualTo(4);
    }

    @Test
    @DisplayName("H65: el reintento se devuelve aunque la conversación ya esté en solo lectura")
    void idempotenciaTrasSoloLectura() throws Exception {
        long id = iniciarEntrePersonas();
        enviarCon(perdedor, id, "Antes de cerrar", "reintento-tardio-01").andExpect(status().isCreated());
        perdido.setEstado(EstadoObjeto.RECUPERADO);
        em.flush();
        enviarCon(perdedor, id, "Antes de cerrar", "reintento-tardio-01").andExpect(status().isOk());
        enviarCon(perdedor, id, "Nuevo", "otra-clave-nueva-02").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("H65: la clave usada en otra conversación responde 409 y un formato inválido 400")
    void idempotenciaErrores() throws Exception {
        long id = iniciarEntrePersonas();
        enviarCon(perdedor, id, "Hola", "clave-unica-0001").andExpect(status().isCreated());
        enviarCon(perdedor, id, "Hola", "corta").andExpect(status().isBadRequest());
        enviarCon(perdedor, id, "Hola", "con espacios no vale").andExpect(status().isBadRequest());

        Objeto otroPerdido = objeto(perdedor, TipoObjeto.PERDIDO, "Llaves perdidas");
        Objeto otroEncontrado = objeto(hallador, TipoObjeto.ENCONTRADO, "Llaves halladas");
        Coincidencia c2 = new Coincidencia();
        c2.setObjetoPerdido(otroPerdido);
        c2.setObjetoEncontrado(otroEncontrado);
        c2.setPorcentaje(BigDecimal.valueOf(70));
        c2.setNivel(NivelCoincidencia.MEDIA);
        c2.setEstado(EstadoCoincidencia.PENDIENTE);
        em.persist(c2);
        Contacto k2 = new Contacto();
        k2.setCoincidencia(c2);
        k2.setUsuarioEmisor(perdedor);
        k2.setUsuarioReceptor(hallador);
        k2.setMensaje("Otra solicitud");
        em.persist(k2);
        em.flush();
        Number otra = JsonPath.read(mvc.perform(post("/api/conversaciones").with(como(perdedor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"contactoId\":" + k2.getId() + "}"))
                .andReturn().getResponse().getContentAsString(), "$.id");
        enviarCon(perdedor, otra.longValue(), "Hola", "clave-unica-0001").andExpect(status().isConflict());
    }

    @Test
    @DisplayName("H65: publicar dos veces con la misma clave crea una sola publicación")
    void idempotenciaPublicacion() throws Exception {
        String cuerpo = "{\"categoriaId\":" + categoria.getId() + ",\"nombre\":\"Paraguas negro\",\"descripcion\":"
                + "\"Paraguas negro plegable\",\"fechaObjeto\":\"" + LocalDate.now().minusDays(1) + "\",\"tipo\":\"PERDIDO\","
                + "\"ubicacion\":\"Biblioteca\"}";
        var pedido = post("/api/objetos").with(como(perdedor)).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                .header("Idempotency-Key", "publicar-paraguas-01");
        Number idObjeto = JsonPath.read(mvc.perform(pedido).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        mvc.perform(post("/api/objetos").with(como(perdedor)).contentType(MediaType.APPLICATION_JSON).content(cuerpo)
                        .header("Idempotency-Key", "publicar-paraguas-01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(idObjeto.longValue()));
        em.flush();
        assertThat(contar("SELECT COUNT(o) FROM Objeto o WHERE o.usuario.id = ?1 AND o.nombre = 'Paraguas negro'",
                perdedor.getId())).isEqualTo(1);
    }
}
