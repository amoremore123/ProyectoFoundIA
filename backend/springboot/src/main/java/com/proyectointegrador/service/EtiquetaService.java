package com.proyectointegrador.service;

import com.proyectointegrador.dto.ChatDtos.ConversacionResponse;
import com.proyectointegrador.entity.Pertenencia;
import com.proyectointegrador.entity.TipoConversacion;
import com.proyectointegrador.exception.LimiteAsistenteException;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.exception.SolicitudInvalidaException;
import com.proyectointegrador.repository.ConversacionRepository;
import com.proyectointegrador.repository.PertenenciaRepository;
import com.proyectointegrador.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Etiqueta QR preventiva. La persona registra sus pertenencias y pega el QR; quien la encuentra
 * escanea, ve solo el nombre y la descripción que el dueño eligió, y le escribe por el chat. Nadie
 * ve el nombre completo, el correo ni el teléfono del dueño.
 */
@Service
public class EtiquetaService {

    static final int MAX_ACTIVAS = 30;
    static final int MAX_AVISOS_POR_DIA = 5;
    private static final Pattern CODIGO = Pattern.compile("[A-Za-z0-9_-]{16,32}");
    private static final Pattern NO_PERMITIDO = Pattern.compile("(https?://|www\\.|<|>)", Pattern.CASE_INSENSITIVE);
    private static final SecureRandom AZAR = new SecureRandom();

    public record PertenenciaResponse(Long id, String nombre, String descripcion, String codigo, LocalDateTime creadaEn) {}

    public record EtiquetaPublica(String nombre, String descripcion, boolean propia) {}

    private final PertenenciaRepository pertenencias;
    private final UsuarioRepository usuarios;
    private final ConversacionRepository conversaciones;
    private final ChatService chat;

    public EtiquetaService(PertenenciaRepository pertenencias, UsuarioRepository usuarios,
                           ConversacionRepository conversaciones, ChatService chat) {
        this.pertenencias = pertenencias;
        this.usuarios = usuarios;
        this.conversaciones = conversaciones;
        this.chat = chat;
    }

    @Transactional(readOnly = true)
    public List<PertenenciaResponse> mias(Long usuarioId) {
        return pertenencias.findByUsuarioIdAndActivaTrueOrderByIdDesc(usuarioId).stream().map(EtiquetaService::respuesta).toList();
    }

    @Transactional
    public PertenenciaResponse crear(Long usuarioId, String nombre, String descripcion) {
        String n = texto(nombre, 80, "El nombre");
        if (n.isEmpty()) throw new SolicitudInvalidaException("Escribe el nombre de la pertenencia.");
        String d = texto(descripcion, 200, "La descripción");
        if (pertenencias.countByUsuarioIdAndActivaTrue(usuarioId) >= MAX_ACTIVAS) {
            throw new SolicitudInvalidaException("Puedes tener hasta " + MAX_ACTIVAS + " etiquetas activas.");
        }
        Pertenencia p = new Pertenencia();
        p.setUsuario(usuarios.findById(usuarioId).orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado")));
        p.setNombre(n);
        p.setDescripcion(d.isEmpty() ? null : d);
        p.setCodigo(codigoNuevo());
        return respuesta(pertenencias.saveAndFlush(p));
    }

    /** Desactivar: el QR deja de funcionar y sus conversaciones quedan en solo lectura. */
    @Transactional
    public void desactivar(Long id, Long usuarioId) {
        Pertenencia p = pertenencias.findById(id)
                .filter(x -> x.isActiva() && x.getUsuario().getId().equals(usuarioId))
                .orElseThrow(() -> new ResourceNotFoundException("Etiqueta no encontrada"));
        p.setActiva(false);
    }

    @Transactional(readOnly = true)
    public EtiquetaPublica publica(String codigo, Long usuarioId) {
        Pertenencia p = activa(codigo);
        return new EtiquetaPublica(p.getNombre(), p.getDescripcion(), p.getUsuario().getId().equals(usuarioId));
    }

    @Transactional
    public ConversacionResponse avisar(String codigo, Long usuarioId, String mensaje) {
        Pertenencia p = activa(codigo);
        if (p.getUsuario().getId().equals(usuarioId)) {
            throw new SolicitudInvalidaException("Esta etiqueta es tuya.");
        }
        boolean yaEscribio = conversaciones.findFirstByPertenenciaIdAndUsuarioAId(p.getId(), usuarioId).isPresent();
        if (!yaEscribio && conversaciones.countByTipoAndUsuarioAIdAndCreadaEnAfter(TipoConversacion.ETIQUETA, usuarioId,
                LocalDateTime.now(ZoneOffset.UTC).minusDays(1)) >= MAX_AVISOS_POR_DIA) {
            throw new LimiteAsistenteException("Ya avisaste a varios dueños hoy. Si encontraste más cosas, entrégalas en la biblioteca.", 3600);
        }
        return chat.iniciarPorEtiqueta(p, usuarioId, mensaje);
    }

    private Pertenencia activa(String codigo) {
        if (codigo == null || !CODIGO.matcher(codigo).matches()) throw noDisponible();
        return pertenencias.findByCodigo(codigo).filter(Pertenencia::isActiva).orElseThrow(this::noDisponible);
    }

    private ResourceNotFoundException noDisponible() {
        return new ResourceNotFoundException("Esta etiqueta no existe o su dueño la desactivó.");
    }

    private static String texto(String valor, int maximo, String campo) {
        String limpio = valor == null ? "" : valor.trim().replaceAll("\\s+", " ");
        if (limpio.length() > maximo) throw new SolicitudInvalidaException(campo + " no puede pasar de " + maximo + " caracteres.");
        // Lo ve cualquiera que escanee: sin enlaces ni formato
        if (NO_PERMITIDO.matcher(limpio).find()) throw new SolicitudInvalidaException(campo + " no puede tener enlaces.");
        return limpio;
    }

    private String codigoNuevo() {
        for (int i = 0; i < 5; i++) {
            byte[] bytes = new byte[16];
            AZAR.nextBytes(bytes);
            String codigo = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (pertenencias.findByCodigo(codigo).isEmpty()) return codigo;
        }
        throw new IllegalStateException("No se pudo generar un código único");
    }

    private static PertenenciaResponse respuesta(Pertenencia p) {
        return new PertenenciaResponse(p.getId(), p.getNombre(), p.getDescripcion(), p.getCodigo(), p.getCreadaEn());
    }
}
