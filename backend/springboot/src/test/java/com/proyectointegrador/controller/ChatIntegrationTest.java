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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.hamcrest.Matchers.containsString;
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

    // Las fotos (H62 y H64) van a una carpeta temporal, no a la del proyecto
    static final Path FOTOS = Path.of(System.getProperty("java.io.tmpdir"), "foundia-chat-" + UUID.randomUUID());

    @DynamicPropertySource
    static void carpetaDeFotos(DynamicPropertyRegistry registro) {
        registro.add("app.upload.directory", FOTOS::toString);
    }

    @Autowired
    private com.proyectointegrador.service.MigradorFotosChat migrador;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbc;

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

    // --- H64: foto de perfil de la otra persona en el chat ---

    private static byte[] png(int lado) throws Exception {
        BufferedImage img = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0x336699);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(img, "png", salida);
        return salida.toByteArray();
    }

    private void subirFotoDePerfil(Usuario u, byte[] datos) throws Exception {
        mvc.perform(multipart("/api/perfil/foto").file(new MockMultipartFile("archivo", "yo.png", "image/png", datos))
                        .with(como(u)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("H64: quien conversa ve la foto de la otra persona; una tercera persona no")
    void fotoDePerfilEnElChat() throws Exception {
        long id = iniciarEntrePersonas();
        byte[] foto = png(6);
        subirFotoDePerfil(hallador, foto);

        mvc.perform(get("/api/conversaciones/{id}/foto", id).with(como(perdedor)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(content().bytes(foto));
        mvc.perform(get("/api/conversaciones/{id}", id).with(como(perdedor)))
                .andExpect(jsonPath("$.otro.tieneFoto").value(true));
        // La otra persona (sin foto) y una tercera reciben 404; sin sesión, 401
        mvc.perform(get("/api/conversaciones/{id}/foto", id).with(como(hallador))).andExpect(status().isNotFound());
        mvc.perform(get("/api/conversaciones/{id}", id).with(como(hallador)))
                .andExpect(jsonPath("$.otro.tieneFoto").value(false));
        mvc.perform(get("/api/conversaciones/{id}/foto", id).with(como(tercero))).andExpect(status().isNotFound());
        mvc.perform(get("/api/conversaciones/{id}/foto", id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/conversaciones/{id}/foto", 999999L).with(como(perdedor))).andExpect(status().isNotFound());
        // La lista de conversaciones también indica que hay foto
        mvc.perform(get("/api/conversaciones").with(como(perdedor)))
                .andExpect(jsonPath("$[0].otro.tieneFoto").value(true));
    }

    @Test
    @DisplayName("H64: con la conversación bloqueada no se ve la foto, y vuelve al desbloquear")
    void fotoDePerfilConBloqueo() throws Exception {
        long id = iniciarEntrePersonas();
        subirFotoDePerfil(hallador, png(5));
        subirFotoDePerfil(perdedor, png(7));
        mvc.perform(post("/api/conversaciones/{id}/bloqueo", id).with(como(perdedor))).andExpect(status().isOk());
        mvc.perform(get("/api/conversaciones/{id}/foto", id).with(como(perdedor))).andExpect(status().isNotFound());
        mvc.perform(get("/api/conversaciones/{id}/foto", id).with(como(hallador))).andExpect(status().isNotFound());
        mvc.perform(get("/api/conversaciones/{id}", id).with(como(hallador)))
                .andExpect(jsonPath("$.otro.tieneFoto").value(false));
        mvc.perform(delete("/api/conversaciones/{id}/bloqueo", id).with(como(perdedor))).andExpect(status().isOk());
        mvc.perform(get("/api/conversaciones/{id}/foto", id).with(como(perdedor))).andExpect(status().isOk());
        mvc.perform(get("/api/conversaciones/{id}/foto", id).with(como(hallador))).andExpect(status().isOk());
    }

    @Test
    @DisplayName("H64: en el chat con la biblioteca el personal ve la foto de quien reclama, y no al revés")
    void fotoDePerfilConLaBiblioteca() throws Exception {
        Objeto custodia = objeto(bibliotecaria, TipoObjeto.ENCONTRADO, "Mochila en custodia");
        custodia.setEstado(EstadoObjeto.EN_CUSTODIA);
        Reclamo reclamo = new Reclamo();
        reclamo.setObjeto(custodia);
        reclamo.setUsuario(tercero);
        em.persist(reclamo);
        em.flush();
        String cuerpo = mvc.perform(post("/api/conversaciones").with(como(tercero))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reclamoId\":" + reclamo.getId() + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
        subirFotoDePerfil(tercero, png(4));
        subirFotoDePerfil(bibliotecaria, png(9));

        mvc.perform(get("/api/conversaciones/{id}/foto", id).with(como(bibliotecaria))).andExpect(status().isOk());
        // Quien reclama habla con «Biblioteca», no con una persona del personal
        mvc.perform(get("/api/conversaciones/{id}/foto", id).with(como(tercero))).andExpect(status().isNotFound());
        mvc.perform(get("/api/conversaciones/{id}", id).with(como(tercero)))
                .andExpect(jsonPath("$.otro.nombre").value("Biblioteca"))
                .andExpect(jsonPath("$.otro.tieneFoto").value(false));
        // Una persona ajena al reclamo tampoco
        mvc.perform(get("/api/conversaciones/{id}/foto", id).with(como(perdedor))).andExpect(status().isNotFound());
    }

    // --- H62: fotos del chat privadas ---

    private long enviarFoto(Usuario autor, long id, byte[] datos, String tipo) throws Exception {
        String cuerpo = mvc.perform(multipart("/api/conversaciones/{id}/mensajes", id)
                        .file(new MockMultipartFile("foto", "foto.png", tipo, datos)).with(como(autor)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
    }

    private String rutaGuardada(long mensajeId) {
        return jdbc.queryForObject("SELECT imagen_url FROM mensajes_chat WHERE id = ?", String.class, mensajeId);
    }

    @Test
    @DisplayName("H62: la foto del chat se guarda en la carpeta privada y solo la ven quienes conversan")
    void fotoDelChatPrivada() throws Exception {
        long id = iniciarEntrePersonas();
        byte[] foto = png(8);
        long mensaje = enviarFoto(perdedor, id, foto, "image/png");
        String ruta = "/api/conversaciones/" + id + "/mensajes/" + mensaje + "/foto";

        mvc.perform(get("/api/conversaciones/{id}/mensajes", id).with(como(hallador)))
                .andExpect(jsonPath("$.mensajes[0].imagenUrl").value(ruta));
        assertThat(rutaGuardada(mensaje)).matches("chat/[0-9a-f-]{36}\\.png");
        assertThat(java.nio.file.Files.isRegularFile(FOTOS.resolve(rutaGuardada(mensaje)))).isTrue();

        for (Usuario participante : new Usuario[]{perdedor, hallador}) {
            mvc.perform(get(ruta).with(como(participante))).andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.IMAGE_PNG))
                    .andExpect(header().string("Cache-Control", containsString("private")))
                    .andExpect(content().bytes(foto));
        }
        mvc.perform(get(ruta).with(como(tercero))).andExpect(status().isNotFound());
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
        // Un id de conversación que no es el del mensaje
        mvc.perform(get("/api/conversaciones/{id}/mensajes/{m}/foto", id + 999, mensaje).with(como(perdedor)))
                .andExpect(status().isNotFound());
        // La ruta pública del archivo está bloqueada, con o sin sesión
        String archivo = "/uploads/" + rutaGuardada(mensaje);
        mvc.perform(get(archivo)).andExpect(status().isUnauthorized());
        mvc.perform(get(archivo).with(como(perdedor))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("H62: el ADMIN ve la foto solo si el mensaje fue reportado (para moderarlo)")
    void fotoDelChatParaModerar() throws Exception {
        Usuario admin = usuario("admin-chat@prueba.invalid", "Ada", Rol.ADMIN);
        long id = iniciarEntrePersonas();
        long mensaje = enviarFoto(hallador, id, png(6), "image/png");
        String ruta = "/api/conversaciones/" + id + "/mensajes/" + mensaje + "/foto";
        mvc.perform(get(ruta).with(como(admin))).andExpect(status().isNotFound());
        mvc.perform(post("/api/conversaciones/{id}/mensajes/{m}/reporte", id, mensaje).with(como(perdedor))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"motivo\":\"Foto inapropiada\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get(ruta).with(como(admin))).andExpect(status().isOk());
        // El reporte no abre la foto a otras personas
        mvc.perform(get(ruta).with(como(tercero))).andExpect(status().isNotFound());
        mvc.perform(get(ruta).with(como(bibliotecaria))).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("H62: una foto que no es imagen o de más de 5 MiB responde 400 y no se guarda nada")
    void fotoDelChatInvalida() throws Exception {
        long id = iniciarEntrePersonas();
        mvc.perform(multipart("/api/conversaciones/{id}/mensajes", id)
                        .file(new MockMultipartFile("foto", "nota.png", "image/png", "no soy una imagen".getBytes())).with(como(perdedor)))
                .andExpect(status().isBadRequest());
        byte[] grande = new byte[5 * 1024 * 1024 + 1];
        System.arraycopy(png(4), 0, grande, 0, png(4).length);
        mvc.perform(multipart("/api/conversaciones/{id}/mensajes", id)
                        .file(new MockMultipartFile("foto", "grande.png", "image/png", grande)).with(como(perdedor)))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM mensajes_chat WHERE conversacion_id = ?", Integer.class, id))
                .isZero();
    }

    @Test
    @DisplayName("H62: las fotos que ya estaban en la carpeta pública se pasan a la privada al arrancar")
    void fotosAnterioresSePasanAPrivadas() throws Exception {
        long id = iniciarEntrePersonas();
        long mensaje = enviarFoto(perdedor, id, png(5), "image/png");
        // Se simula una foto guardada con la versión anterior: en la carpeta pública y con ruta /uploads/
        String nombre = UUID.randomUUID() + ".png";
        byte[] anterior = png(3);
        java.nio.file.Files.write(FOTOS.resolve(nombre), anterior);
        jdbc.update("UPDATE mensajes_chat SET imagen_url = ? WHERE id = ?", "/uploads/" + nombre, mensaje);
        em.clear();  // el cambio por SQL directo no lo ve la caché de Hibernate de esta prueba
        String ruta = "/api/conversaciones/" + id + "/mensajes/" + mensaje + "/foto";
        // Mientras no se mueva, se sigue entregando por la ruta con sesión
        mvc.perform(get(ruta).with(como(hallador))).andExpect(status().isOk()).andExpect(content().bytes(anterior));

        assertThat(migrador.migrar()).isEqualTo(1);
        em.clear();
        assertThat(rutaGuardada(mensaje)).isEqualTo("chat/" + nombre);
        assertThat(java.nio.file.Files.exists(FOTOS.resolve(nombre))).isFalse();
        assertThat(java.nio.file.Files.isRegularFile(FOTOS.resolve("chat").resolve(nombre))).isTrue();
        mvc.perform(get(ruta).with(como(hallador))).andExpect(status().isOk()).andExpect(content().bytes(anterior));
        // Otra vez no hace nada
        assertThat(migrador.migrar()).isZero();
    }

    @Test
    @DisplayName("H62: en el chat con la biblioteca el personal ve la foto de quien reclama")
    void fotoDelChatConLaBiblioteca() throws Exception {
        Objeto custodia = objeto(bibliotecaria, TipoObjeto.ENCONTRADO, "Llaves en custodia");
        custodia.setEstado(EstadoObjeto.EN_CUSTODIA);
        Reclamo reclamo = new Reclamo();
        reclamo.setObjeto(custodia);
        reclamo.setUsuario(tercero);
        em.persist(reclamo);
        em.flush();
        String cuerpo = mvc.perform(post("/api/conversaciones").with(como(tercero))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reclamoId\":" + reclamo.getId() + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(cuerpo, "$.id")).longValue();
        long mensaje = enviarFoto(tercero, id, png(7), "image/png");
        String ruta = "/api/conversaciones/" + id + "/mensajes/" + mensaje + "/foto";
        mvc.perform(get(ruta).with(como(bibliotecaria))).andExpect(status().isOk());
        mvc.perform(get(ruta).with(como(tercero))).andExpect(status().isOk());
        mvc.perform(get(ruta).with(como(perdedor))).andExpect(status().isNotFound());
    }
}
