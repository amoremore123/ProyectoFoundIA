package com.proyectointegrador.controller;

import com.proyectointegrador.dto.ObjetoRequest;
import com.proyectointegrador.entity.*;
import com.proyectointegrador.repository.*;
import com.proyectointegrador.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

abstract class Sprint2Fixtures {
    @Autowired MockMvc api;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired ObjetoRepository objetos;
    @Autowired NotificacionRepository notificaciones;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired EventoCoincidenciaRepository eventos;
    @Autowired org.springframework.context.ApplicationEventPublisher publicador;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("avisosCoincidenciaExecutor") java.util.concurrent.Executor avisosExecutor;
    Usuario ana, luis, admin;
    Categoria categoria;

    @BeforeEach void prepararBase() throws Exception {
        limpiarBase(); ana = usuario("Ana", Rol.USUARIO); luis = usuario("Luis", Rol.USUARIO); admin = usuario("Admin", Rol.ADMIN);
        categoria = new Categoria(); categoria.setNombre("Categoría " + UUID.randomUUID()); categoria = categorias.saveAndFlush(categoria);
    }

    @AfterEach void limpiarBase() throws Exception {
        esperarAvisos();
        // Lista cerrada y orden de FK. Esta fixture solo se usa en H2 o en MySQL QA con token.
        var existentes = new HashSet<String>();
        try (var c = jdbc.getDataSource().getConnection(); var tablas = c.getMetaData().getTables(c.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tablas.next()) existentes.add(tablas.getString("TABLE_NAME").toLowerCase(java.util.Locale.ROOT));
        }
        for (String tabla : List.of("eventos_coincidencia", "avisos_coincidencia", "contactos", "reclamos", "notificaciones",
                "preferencias_notificacion", "coincidencias", "reportes", "fotos", "objetos", "categorias", "usuarios")) {
            if (existentes.contains(tabla)) jdbc.update("DELETE FROM " + tabla);
        }
    }

    Usuario usuario(String nombre, Rol rol) {
        Usuario u = new Usuario(); u.setNombre(nombre); u.setApellido("Reservado"); u.setCorreo(UUID.randomUUID() + "@prueba.invalid");
        u.setPassword("no-se-usa"); u.setVerificado(true); u.setRol(rol); return usuarios.saveAndFlush(u);
    }

    Objeto objeto(Usuario usuario, TipoObjeto tipo) {
        Objeto o = new Objeto(); o.setUsuario(usuario); o.setCategoria(categoria); o.setNombre("Mochila azul");
        o.setDescripcion("Descripción QA"); o.setUbicacion("Biblioteca"); o.setFechaObjeto(LocalDate.now().minusDays(1)); o.setTipo(tipo);
        return objetos.saveAndFlush(o);
    }

    ObjetoRequest cambio(Objeto o) {
        return new ObjetoRequest(categoria.getId(), o.getNombre(), "Descripción editada", o.getFechaObjeto(), o.getTipo(), o.getUbicacion(), null, null);
    }

    RequestPostProcessor como(Usuario u) {
        var p = UserPrincipal.from(u);
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    void emitirEvento(Long objetoId) {
        tx.executeWithoutResult(t -> {
            var evento=new EventoCoincidencia();evento.setObjeto(objetos.findById(objetoId).orElseThrow());eventos.saveAndFlush(evento);
            publicador.publishEvent(new com.proyectointegrador.service.ObjetoGuardado(objetoId));
        });
        esperarAvisos();
    }

    void esperarAvisos(){esperarAvisos(avisosExecutor);}
    static void esperarAvisos(java.util.concurrent.Executor executor){
        var pool=(org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor)executor;
        org.awaitility.Awaitility.await().atMost(15,java.util.concurrent.TimeUnit.SECONDS)
                .until(() -> pool.getActiveCount()==0 && pool.getThreadPoolExecutor().getQueue().isEmpty());
    }
}
